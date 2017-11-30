package nnet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.log4j.Logger;

import nnet.activation.Constant;
import nnet.activation.Function;
import nnet.activation.Identity;
import nnet.activation.TanH;
import spawnn.utils.DataFrame;
import spawnn.utils.DataUtils;
import spawnn.utils.DataUtils.Transform;

public class AirfoilTest {

	private static Logger log = Logger.getLogger(AirfoilTest.class);

	public static void main(String[] args) {
		Random r = new Random();

		DataFrame df = DataUtils.readDataFrameFromCSV(new File("data/airfoil_self_noise.csv"), new int[] {}, true);
		for (int i = 0; i < df.names.size(); i++)
			log.debug(i + ":" + df.names.get(i));
		
		List<double[]> samples = df.samples;
		int[] fa = new int[]{ 0, 1, 2, 3, 4};
		int ta = 5;
		
		DataUtils.transform(samples, fa, Transform.zScore);
		//DataUtils.transform(samples, fa, Transform.scale01);
						
		List<Entry<List<Integer>, List<Integer>>> cvList = SupervisedUtils.getCVList(10, 1, samples.size());
		
		for (double lr : new double[] { 0.0005 }) {
			log.debug("lr: "+lr);
			DescriptiveStatistics dsRMSE = new DescriptiveStatistics();
						
			for (final Entry<List<Integer>, List<Integer>> cvEntry : cvList) {
				List<double[]> samplesTrain = new ArrayList<double[]>();
				for (int k : cvEntry.getKey()) 
					samplesTrain.add(samples.get(k));
					
				List<double[]> samplesVal = new ArrayList<double[]>();
				for (int k : cvEntry.getValue()) 
					samplesVal.add(samples.get(k));
				
				List<Function> input = new ArrayList<Function>();
				for (int i = 0; i < fa.length; i++) 
					input.add(new Identity());
				input.add(new Constant(1.0));

				List<Function> hidden1 = new ArrayList<Function>();
				for (int i = 0; i < 48; i++)
					hidden1.add(new TanH());
				hidden1.add(new Constant(1.0));
				
				List<Function> hidden2 = new ArrayList<Function>();
				for (int i = 0; i < 48; i++)
					hidden2.add(new TanH());
				hidden2.add(new Constant(1.0));

				NNet nnet = new NNet(new Function[][] { 
					input.toArray(new Function[] {}), 
					hidden1.toArray(new Function[] {}), 
					hidden2.toArray(new Function[] {}), 
					new Function[] { new Identity() } }, 
						lr );

				double bestE = Double.POSITIVE_INFINITY;
				int noImp = 0;
				for (int i = 0;; i++) {
					double[] d = samplesTrain.get( r.nextInt(samplesTrain.size()) );
					nnet.train(i, DataUtils.strip(d, fa), new double[]{ d[ta] } );
										
					if (i % 10000 == 0) {
						List<Double> responseVal = new ArrayList<>();
						for (double[] d2 : samplesVal) 
							responseVal.add(nnet.present(DataUtils.strip(d2, fa))[0]);
						double e = SupervisedUtils.getRMSE(responseVal, samplesVal, ta);
						
						if( Double.isNaN(e) || noImp == 10 )
							break;
						
						if ( e <= bestE ) {
							bestE = e;
							noImp = 0;
						} else 
							noImp++;
					}
					
				}
				dsRMSE.addValue(bestE);
			}
			log.debug("RMSE: " + dsRMSE.getMean());
		}
	}
}