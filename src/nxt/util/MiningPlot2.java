package nxt.util;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

import fr.cryptohash.Shabal256;
import fr.cryptohash.Groestl512;

public class MiningPlot2 {
	public final static int NONCE_SIZE = 8;
	public final static int NUM_NONCES = 2;
	public final static int TOTAL_NONCE_SIZE = NONCE_SIZE * NUM_NONCES;
	public final static int NONCE_DELTA_SIZE = 4;
	public final static int NUM_SCOOPS = 65536;
	public final static int NONCE_SET_SIZE = NUM_SCOOPS * NONCE_DELTA_SIZE;
	
	final long addr;
	final long nonce1;
	final long nonce2;
	byte[] hash;
	
	public MiningPlot2(long addr, long nonce1, long nonce2) {
		this.addr = addr;
		this.nonce1 = nonce1;
		this.nonce2 = nonce2;
		this.hash = null;
	}
	
	public boolean checkTarget(long target) {
		ByteBuffer baseBuffer = ByteBuffer.allocate(24);
		baseBuffer.putLong(addr);
		baseBuffer.putLong(nonce1);
		baseBuffer.putLong(nonce2);
		byte[] base = baseBuffer.array();
		Groestl512 md = new Groestl512();
		md.update(base);
		hash = md.digest();
		BigInteger resultTarget = new BigInteger(1,  new byte[] {hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0]});
		return resultTarget.compareTo(BigInteger.valueOf(target)) < 0;
	}
	
	public int getScoop() {
		if(hash == null)
			checkTarget(0);
		
		Groestl512 md = new Groestl512();
		md.update(hash);
		byte[] hash2 = md.digest();
		BigInteger resultScoop = new BigInteger(1,  new byte[] {hash2[7], hash2[6], hash2[5], hash2[4], hash2[3], hash2[2], hash2[1], hash2[0]});
		return resultScoop.mod(BigInteger.valueOf(NUM_SCOOPS)).intValue();
	}
}
