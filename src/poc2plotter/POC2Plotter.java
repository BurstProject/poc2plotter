package poc2plotter;

import static org.jocl.CL.*;
import nxt.util.Convert;

import org.jocl.*;

public class POC2Plotter {

	public static void main(String[] args) {
		if(args.length < 1) {
			System.out.println("POC2Plotter:\n"
					+ "list devices: list\n"
					+ "cpu plot: cpu threads id nonce2 noncesets target\n"
					+ "gpu plot: gpu platform:device:managingthreads:worksizemultiplier id nonce2 noncesets target");
			return;
		}
		
		String operation = args[0];
		
		if(operation.equalsIgnoreCase("list")) {
			list();
			return;
		}
		
		if(!operation.equalsIgnoreCase("cpu") &&
				!operation.equalsIgnoreCase("gpu")) {
			System.out.println("Invalid operation");
			return;
		}
		
		if(args.length < 6) {
			System.out.println("Insufficient parameters");
			return;
		}
		
		long id;
		long nonce2;
		long nonceSets;
		long target;
		
		Plotter plotter = null;
		
		try {
			id = Convert.parseUnsignedLong(args[2]);
			nonce2 = Convert.parseUnsignedLong(args[3]);
			nonceSets = Convert.parseUnsignedLong(args[4]);
			target = Convert.parseUnsignedLong(args[5]);
			
			switch(operation.toLowerCase()) {
			case "cpu":
				plotter = new CPUPlotter(id, nonce2, nonceSets, target, args[1]);
				break;
			case "gpu":
				plotter = new GPUPlotter(id, nonce2, nonceSets, target, args[1]);
				break;
			default:
				return; // should never get here
			}
		}
		catch(IllegalArgumentException e) {
			System.out.println("Failed to parse numbers");
			return;
		}
		
		plotter.plot();
	}
	
	static void list() {
		int numPlatforms[] = new int[1];
		clGetPlatformIDs(0, null, numPlatforms);
		
		cl_platform_id platforms[] = new cl_platform_id[numPlatforms[0]];
		clGetPlatformIDs(platforms.length, platforms, null);
		
		for(int i = 0; i < numPlatforms[0]; i++) {
			int[] numDevices = new int[1];
			clGetDeviceIDs(platforms[i], CL_DEVICE_TYPE_GPU, 0, null, numDevices);
			
			cl_device_id devices[] = new cl_device_id[numDevices[0]];
			clGetDeviceIDs(platforms[i], CL_DEVICE_TYPE_GPU, devices.length, devices, null);
			
			long[] size = new long[1];
			clGetPlatformInfo(platforms[i], CL_PLATFORM_NAME, 0, null, size);
			
			byte buffer[] = new byte[(int)size[0]];
			clGetPlatformInfo(platforms[i], CL_PLATFORM_NAME, buffer.length, Pointer.to(buffer), null);
			String platformName = new String(buffer, 0, buffer.length - 1);
			
			System.out.println("Platform " + i + ": " + platformName);
			
			for(int j = 0; j < numDevices[0]; j++) {
				clGetDeviceInfo(devices[j], CL_DEVICE_NAME, 0, null, size);
				
				buffer = new byte[(int)size[0]];
				clGetDeviceInfo(devices[j], CL_DEVICE_NAME, buffer.length, Pointer.to(buffer), null);
				String deviceName = new String(buffer, 0, buffer.length - 1);
				
				System.out.println("\tDevice " + j + ": " + deviceName);
			}
		}
	}

}
