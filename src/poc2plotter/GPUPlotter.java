package poc2plotter;

import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Paths.get;
import static org.jocl.CL.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import org.jocl.*;

public class GPUPlotter extends Plotter {
	
	int platformId;
	int deviceId;
	long workSizeMult;
	
	cl_context context;
	cl_command_queue queue[];
	cl_program program;
	cl_kernel kernel;
	
	cl_mem outMem[];
	
	long numWorkItems;
	long numNonces;
	
	long nextStartNonce = 0;
	
	private final Map<Long, int[]> pendingResults = new ConcurrentSkipListMap<>();
	
	GPUPlotter(long id, long nonce2, long nonceSets, long target, String params) {
		super(id, nonce2, nonceSets, target);
		
		try {
			String[] gpuParams = params.split(":", 4);
			platformId = Integer.parseInt(gpuParams[0]);
			deviceId = Integer.parseInt(gpuParams[1]);
			int threads = Integer.parseInt(gpuParams[2]);
			outMem = new cl_mem[threads];
			queue = new cl_command_queue[threads];
			workSizeMult = Long.parseLong(gpuParams[3]);
		}
		catch(Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Invalid gpu params", e);
		}
	}

	@Override
	void plot() {
		initOCL();
		
		Thread proc = new Thread(this.new GPUProcessor());
		proc.start();
		Thread[] controllers = new Thread[queue.length];
		for(int i = 0; i < queue.length; i++) {
			controllers[i] = new Thread(this.new GPUController(i));
			controllers[i].start();
		}
		
		try {
			proc.join();
			for(int i = 0; i < queue.length; i++)
				controllers[i].join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		releaseOCL();
	}
	
	private void initOCL() {
		
		CL.setExceptionsEnabled(true);
		
		int numPlatforms[] = new int[1];
		clGetPlatformIDs(0, null, numPlatforms);
		
		if(platformId >= numPlatforms[0]) {
			throw new ArrayIndexOutOfBoundsException("Invalid platform id");
		}
		
		cl_platform_id platforms[] = new cl_platform_id[numPlatforms[0]];
		clGetPlatformIDs(platforms.length, platforms, null);
		
		int[] numDevices = new int[1];
		clGetDeviceIDs(platforms[platformId], CL_DEVICE_TYPE_GPU, 0, null, numDevices);
		
		if(deviceId >= numDevices[0]) {
			throw new ArrayIndexOutOfBoundsException("Invalid device id");
		}
		
		cl_device_id devices[] = new cl_device_id[numDevices[0]];
		clGetDeviceIDs(platforms[platformId], CL_DEVICE_TYPE_GPU, devices.length, devices, null);
		
		cl_context_properties contextProperties = new cl_context_properties();
		contextProperties.addProperty(CL_CONTEXT_PLATFORM, platforms[platformId]);
		
		context = clCreateContext(contextProperties, 1, new cl_device_id[]{devices[deviceId]}, null, null, null);
		
		for(int i = 0; i < queue.length; i++) {
			queue[i] = clCreateCommandQueue(context, devices[deviceId], 0, null);
		}
		
		String kernelSource;
		try {
			kernelSource = new String(readAllBytes(get("kernel/poc2plot.cl")));
		} catch (IOException e) {
			throw new RuntimeException("Failed to read poc2plot.cl file", e);
		}
		
		program = clCreateProgramWithSource(context, 1, new String[]{kernelSource}, null, null);
		clBuildProgram(program, 0, null, "-I kernel", null, null);
		
		kernel = clCreateKernel(program, "calculate_scoops", null);
		
		long[] maxWorkGroupSize = new long[1];
		clGetKernelWorkGroupInfo(kernel, devices[deviceId], CL_KERNEL_WORK_GROUP_SIZE, 8, Pointer.to(maxWorkGroupSize), null);
		System.out.println("Max work group size: " + maxWorkGroupSize[0]);
		
		long[] maxComputeUnits = new long[1];
		clGetDeviceInfo(devices[deviceId], CL_DEVICE_MAX_COMPUTE_UNITS, 8, Pointer.to(maxComputeUnits), null);
		System.out.println("Max compute units: " + maxComputeUnits[0]);
		
		numWorkItems = maxWorkGroupSize[0] * maxComputeUnits[0];
		numNonces = numWorkItems * workSizeMult;
		
		for(int i = 0; i < outMem.length; i++) {
			outMem[i] = clCreateBuffer(context, CL_MEM_WRITE_ONLY, 4 * numNonces, null, null);
		}
	}
	
	private void releaseOCL() {
		for(int i = 0; i < outMem.length; i++) {
			clReleaseMemObject(outMem[i]);
		}
		clReleaseKernel(kernel);
		clReleaseProgram(program);
		for(int i = 0; i < queue.length; i++) {
			clReleaseCommandQueue(queue[i]);
		}
		clReleaseContext(context);
	}
	
	class GPUController implements Runnable {

		final private int num;
		
		GPUController(int num) {
			this.num = num;
		}
		
		@Override
		public void run() {
			long[] idParam = new long[]{id};
			long[] nonce2Param = new long[]{nonce2};
			long[] targetParam = new long[]{target};
			
			synchronized(kernel) {
				clSetKernelArg(kernel, 0, 8, Pointer.to(idParam));
				clSetKernelArg(kernel, 2, 8, Pointer.to(nonce2Param));
				clSetKernelArg(kernel, 3, 8, Pointer.to(targetParam));
			}
			
			long[] startNonceParam = new long[1];
			int[] resultScoops = null;
			
			while(!isComplete()) {
				synchronized(kernel) {
					startNonceParam[0] = nextStartNonce;
					nextStartNonce += numNonces;
					clSetKernelArg(kernel, 1, 8, Pointer.to(startNonceParam));
					clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(outMem[num]));
					clEnqueueNDRangeKernel(queue[num], kernel, 1, null, new long[]{numNonces}, new long[]{1}, 0, null, null);
				}
				resultScoops = new int[(int)numNonces];
				clEnqueueReadBuffer(queue[num], outMem[num], true, 0, numNonces * 4, Pointer.to(resultScoops), 0, null, null);
				pendingResults.put(startNonceParam[0], resultScoops);
				startNonceParam[0] += numNonces;
			}
		}
	}
	
	class GPUProcessor implements Runnable {

		@Override
		public void run() {
			long nextNonce = 0;
			while(!isComplete()) {
				
				while(pendingResults.containsKey(nextNonce)) {
					processNonces(nextNonce, pendingResults.get(nextNonce));
					pendingResults.remove(nextNonce);
					nextNonce += numNonces;
				}
				
				try {
					System.out.println("sleeping");
					Thread.sleep(200);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}
}
