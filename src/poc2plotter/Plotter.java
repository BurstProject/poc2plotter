package poc2plotter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nxt.util.Convert;
import nxt.util.MiningPlot2;

public abstract class Plotter {
	
	final protected long id;
	final protected long nonce2;
	final protected long nonceSets;
	final protected long target;
	
	final static private long BUFFER_NONCES = 8192;
	final static private long BUFFER_SIZE = BUFFER_NONCES * MiningPlot2.NONCE_DELTA_SIZE;
	
	final protected long scoopSize;
	protected int scoopsCompleted;
	
	private RandomAccessFile outFile;
	private Map<Integer, OutBuffer> outBuffers = new HashMap<>();
	
	Plotter(long id, long nonce2, long nonceSets, long target) {
		this.id = id;
		this.nonce2 = nonce2;
		this.nonceSets = nonceSets;
		this.target = target;
		
		this.scoopSize = this.nonceSets * MiningPlot2.NONCE_DELTA_SIZE;
		this.scoopsCompleted = 0;
		
		createFile();
		allocateFile();
		allocateBuffers();
	}
	
	private void createFile() {
		String filename = Convert.toUnsignedLong(id) + "_"
				+ Convert.toUnsignedLong(nonce2) + "_"
				+ Convert.toUnsignedLong(nonceSets) + "_"
				+ Convert.toUnsignedLong(target);
		
		try {
			outFile = new RandomAccessFile(new File("plots/" + filename), "rw");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException("Failed to create file", e);
		}
	}
	
	private void allocateFile() {
		long totalSize = nonceSets * MiningPlot2.NONCE_SET_SIZE;
		byte[] emptyBytes = new byte[104857600]; // start with 100MB
		long fullWrites = totalSize / 104857600L;
		long remaining = totalSize % 104857600L;
		try {
			for(long i = 0; i < fullWrites; i++) {
				outFile.write(emptyBytes);
			}
			if(remaining != 0) {
				emptyBytes = new byte[(int) remaining];
				outFile.write(emptyBytes);
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Failed to allocate file", e);
		}
	}
	
	private void allocateBuffers() {
		for(int i = 0; i < MiningPlot2.NUM_SCOOPS; i++) {
			outBuffers.put(i, this.new OutBuffer(i));
		}
	}
	
	abstract void plot();
	
	boolean isComplete() {
		return scoopsCompleted == MiningPlot2.NUM_SCOOPS;
	}
	
	void processNonces(List<PlotResult> results) {
		for(PlotResult result : results) {
			outBuffers.get(result.scoop).put(result.nonce);
		}
	}
	
	void processNonces(long startNonce, int[] resultScoops) {
		for(int i = 0; i < resultScoops.length; i++) {
			if(resultScoops[i] != -1) {
				outBuffers.get(resultScoops[i]).put(startNonce + i);
			}
		}
	}
	
	static class PlotResult implements Comparable<PlotResult> {
		
		final public long nonce;
		final public int scoop;
		
		PlotResult(long nonce, int scoop) {
			this.nonce = nonce;
			this.scoop = scoop;
		}

		@Override
		public int compareTo(PlotResult pr) {
			return Long.compare(this.nonce, pr.nonce);
		}
	}
	
	class OutBuffer {
		final int scoop;
		ByteBuffer buffer;
		long written;
		long prevNonce;
		boolean complete;
		
		OutBuffer(int scoop) {
			this.scoop = scoop;
			buffer = ByteBuffer.allocate((int) BUFFER_SIZE);
			written = 0;
			prevNonce = 0L;
			complete = false;
		}
		
		void put(long nonce) {
			if(complete)
				return;
			
			long delta = nonce - prevNonce;
			
			if(delta <= Integer.MAX_VALUE) { // expect this to almost always be true
				buffer.putInt((int)delta);
				write();
			}
			else {
				buffer.putInt(((int)(delta >>> 32)) | Integer.MIN_VALUE);
				write();
				if(!complete) {
					buffer.putInt((int)(delta & 0xFFFFFFFF));
					write();
				}
			}
			
			prevNonce = nonce;
		}
		
		private void write() {
			if(buffer.position() % 256 == 0) {
				System.out.println("Scoop: " + scoop + " pos: " + buffer.position());
			}
			if(buffer.position() == BUFFER_SIZE
					|| buffer.position() + written == scoopSize) {
				try {
					outFile.seek(scoop * scoopSize + written);
					outFile.write(buffer.array(), 0, buffer.position());
					written += buffer.position();
					buffer.clear();
					if(written == scoopSize) {
						complete = true;
						scoopsCompleted++;
					}
				} catch (IOException e) {
					e.printStackTrace();
					throw new RuntimeException("Failed to write plot data", e);
				}
			}
		}
	}
}
