package poc2plotter;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import nxt.util.MiningPlot2;
import fr.cryptohash.Groestl512;

public class CPUPlotter extends Plotter {
	
	int threads;
	final long workSize = 1024;
	Long nextWorkNonce = 0L;
	long nextPendingNonce = 0L;
	
	private final Map<Long, List<PlotResult>> pendingResults = new ConcurrentSkipListMap<>();
	
	CPUPlotter(long id, long nonce2, long nonceSets, long target, String params) {
		super(id, nonce2, nonceSets, target);
		
		try {
			threads = Integer.parseInt(params);
		}
		catch(Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Invalid cpu params", e);
		}
	}

	@Override
	void plot() {
		Thread proc = new Thread(this.new CPUProcessor());
		proc.start();
		for(int i = 0; i < threads; i++) {
			Thread worker = new Thread(this.new CPUWorker());
			worker.start();
		}
		try {
			proc.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	long getWork() {
		long work;
		synchronized(nextWorkNonce) {
			work = nextWorkNonce;
			nextWorkNonce += workSize;
		}
		return work;
	}
	
	void submitWork(long startNonce, List<PlotResult> results) {
		pendingResults.put(startNonce, results);
	}
	
	class CPUProcessor implements Runnable {

		@Override
		public void run() {
			while(!isComplete()) {
				while(pendingResults.containsKey(nextPendingNonce)) {
					List<PlotResult> result = pendingResults.get(nextPendingNonce);
					processNonces(result);
					nextPendingNonce += workSize;
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
	
	class CPUWorker implements Runnable {
		
		@Override
		public void run() {
			System.out.println("worker started");
			Groestl512 md = new Groestl512();
			ByteBuffer baseBuffer = ByteBuffer.allocate(24);
			
			while(!isComplete()) {
				long startNonce = getWork();
				List<PlotResult> results = new ArrayList<>();
				
				for(long i = startNonce; i < startNonce + workSize; i++) {
					baseBuffer.putLong(id);
					baseBuffer.putLong(i);
					baseBuffer.putLong(nonce2);
					byte[] hash = md.digest(baseBuffer.array());
					baseBuffer.clear();
					BigInteger resultTarget = new BigInteger(1,  new byte[] {hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0]});
					
					if(resultTarget.compareTo(BigInteger.valueOf(target)) >= 0)
						continue;
					
					byte[] hash2 = md.digest(hash);
					BigInteger resultScoop = new BigInteger(1,  new byte[] {hash2[7], hash2[6], hash2[5], hash2[4], hash2[3], hash2[2], hash2[1], hash2[0]});
					int scoop = resultScoop.mod(BigInteger.valueOf(MiningPlot2.NUM_SCOOPS)).intValue();
					results.add(new PlotResult(i, scoop));
				}
				submitWork(startNonce, results);
			}
		}
		
	}
}
