#if __ENDIAN_LITTLE__
#define SPH_LITTLE_ENDIAN 1
#else
#define SPH_BIG_ENDIAN 1
#endif

#define SPH_UPTR sph_u64

typedef unsigned int sph_u32;
typedef int sph_s32;
#ifndef __OPENCL_VERSION__
typedef unsigned long long sph_u64;
typedef long long sph_s64;
#else
typedef unsigned long sph_u64;
typedef long sph_s64;
#endif

#define SPH_64 1
#define SPH_64_TRUE 1

#define SPH_C32(x)    ((sph_u32)(x ## U))
#define SPH_T32(x)    ((x) & SPH_C32(0xFFFFFFFF))
#define SPH_ROTL32(x, n)   SPH_T32(((x) << (n)) | ((x) >> (32 - (n))))
#define SPH_ROTR32(x, n)   SPH_ROTL32(x, (32 - (n)))

#define SPH_C64(x)    ((sph_u64)(x ## UL))
#define SPH_T64(x)    ((x) & SPH_C64(0xFFFFFFFFFFFFFFFF))
#define SPH_ROTL64(x, n)   SPH_T64(((x) << (n)) | ((x) >> (64 - (n))))
#define SPH_ROTR64(x, n)   SPH_ROTL64(x, (64 - (n)))

#include "groestl.cl"

#define SWAP4(x) as_uint(as_uchar4(x).wzyx)
#define SWAP8(x) as_ulong(as_uchar8(x).s76543210)

#if SPH_BIG_ENDIAN
    #define ENC64E(x) SWAP8(x)
    #define DEC64E(x) SWAP8(*(const __global sph_u64 *) (x));
#else
    #define ENC64E(x) (x)
    #define DEC64E(x) (*(const __global sph_u64 *) (x));
#endif

#define ROL32(x, n)  rotate(x, (uint) n)
#define SHR(x, n)    ((x) >> n)
#define SWAP32(a)    (as_uint(as_uchar4(a).wzyx))

#define S0(x) (ROL32(x, 25) ^ ROL32(x, 14) ^  SHR(x, 3))
#define S1(x) (ROL32(x, 15) ^ ROL32(x, 13) ^  SHR(x, 10))

#define S2(x) (ROL32(x, 30) ^ ROL32(x, 19) ^ ROL32(x, 10))
#define S3(x) (ROL32(x, 26) ^ ROL32(x, 21) ^ ROL32(x, 7))

#define P(a,b,c,d,e,f,g,h,x,K)                  \
{                                               \
    temp1 = h + S3(e) + F1(e,f,g) + (K + x);      \
    d += temp1; h = temp1 + S2(a) + F0(a,b,c);  \
}

#define PLAST(a,b,c,d,e,f,g,h,x,K)                  \
{                                               \
    d += h + S3(e) + F1(e,f,g) + (x + K);              \
}

#define F0(y, x, z) bitselect(z, y, z ^ x)
#define F1(x, y, z) bitselect(z, y, x)

#define R0 (W0 = S1(W14) + W9 + S0(W1) + W0)
#define R1 (W1 = S1(W15) + W10 + S0(W2) + W1)
#define R2 (W2 = S1(W0) + W11 + S0(W3) + W2)
#define R3 (W3 = S1(W1) + W12 + S0(W4) + W3)
#define R4 (W4 = S1(W2) + W13 + S0(W5) + W4)
#define R5 (W5 = S1(W3) + W14 + S0(W6) + W5)
#define R6 (W6 = S1(W4) + W15 + S0(W7) + W6)
#define R7 (W7 = S1(W5) + W0 + S0(W8) + W7)
#define R8 (W8 = S1(W6) + W1 + S0(W9) + W8)
#define R9 (W9 = S1(W7) + W2 + S0(W10) + W9)
#define R10 (W10 = S1(W8) + W3 + S0(W11) + W10)
#define R11 (W11 = S1(W9) + W4 + S0(W12) + W11)
#define R12 (W12 = S1(W10) + W5 + S0(W13) + W12)
#define R13 (W13 = S1(W11) + W6 + S0(W14) + W13)
#define R14 (W14 = S1(W12) + W7 + S0(W15) + W14)
#define R15 (W15 = S1(W13) + W8 + S0(W0) + W15)

#define RD14 (S1(W12) + W7 + S0(W15) + W14)
#define RD15 (S1(W13) + W8 + S0(W0) + W15)

#define NUM_SCOOPS 65536

__kernel void calculate_scoops(unsigned long id, unsigned long start_nonce, unsigned long nonce_2, unsigned long target, __global int* output) {
	uint gid= get_global_id(0);
	
	__local sph_u64 H[16];
	__local sph_u64 g[16], m[16];
	__local sph_u64 xH[16];
	
	unsigned long nonce_1 = start_nonce + gid;
	
	for (unsigned int u = 0; u < 15; u ++)
		H[u] = 0;
	
#if USE_LE
	H[15] = ((sph_u64)(512 & 0xFF) << 56) | ((sph_u64)(512 & 0xFF00) << 40);
#else
	H[15] = (sph_u64)512;
#endif
	
	m[0] = SWAP8(id);
	m[1] = SWAP8(nonce_1);
	m[2] = SWAP8(nonce_2);
	m[3] = 0x80;
	m[4] = 0;
	m[5] = 0;
	m[6] = 0;
	m[7] = 0;
	m[8] = 0;
	m[9] = 0;
	m[10] = 0;
	m[11] = 0;
	m[12] = 0;
	m[13] = 0;
	m[14] = 0;
	m[15] = 0x0100000000000000;
	
	for (unsigned int u = 0; u < 16; u ++)
		g[u] = m[u] ^ H[u];
	PERM_BIG_P(g);
	PERM_BIG_Q(m);
	for (unsigned int u = 0; u < 16; u ++)
		H[u] ^= g[u] ^ m[u];
	for (unsigned int u = 0; u < 16; u ++)
		xH[u] = H[u];
	PERM_BIG_P(xH);
	for (unsigned int u = 0; u < 16; u ++)
		H[u] ^= xH[u];
	
	int scoop = -1;
	
	if(H[8] < target) {
		for(unsigned int u = 0; u < 8; u++)
			m[u] = H[u + 8];
		
		m[8] = 0x80;
		m[9] = 0;
		m[10] = 0;
		m[11] = 0;
		m[12] = 0;
		m[13] = 0;
		m[14] = 0;
		m[15] = 0x0100000000000000;
		
		for (unsigned int u = 0; u < 15; u ++)
			H[u] = 0;
		
#if USE_LE
		H[15] = ((sph_u64)(512 & 0xFF) << 56) | ((sph_u64)(512 & 0xFF00) << 40);
#else
		H[15] = (sph_u64)512;
#endif
		
		for (unsigned int u = 0; u < 16; u ++)
			g[u] = m[u] ^ H[u];
		PERM_BIG_P(g);
		PERM_BIG_Q(m);
		for (unsigned int u = 0; u < 16; u ++)
			H[u] ^= g[u] ^ m[u];
		for (unsigned int u = 0; u < 16; u ++)
			xH[u] = H[u];
		PERM_BIG_P(xH);
		for (unsigned int u = 0; u < 16; u ++)
			H[u] ^= xH[u];
		
		scoop = H[8] % NUM_SCOOPS;
	}
	
	output[gid] = scoop;
}