package nnet;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

public class SupervisedUtils {
	public static List<Entry<List<Integer>, List<Integer>>> getCVList(int numFolds, int numRepeats, int numSamples) {
		List<Entry<List<Integer>, List<Integer>>> cvList = new ArrayList<Entry<List<Integer>, List<Integer>>>();
		for (int repeat = 0; repeat < numRepeats; repeat++) {
			if (numFolds == 0) { // full
				List<Integer> l = new ArrayList<Integer>();
				for (int i = 0; i < numSamples; i++)
					l.add(i);
				Collections.shuffle(l);
				List<Integer> train = new ArrayList<Integer>(l);
				List<Integer> val = new ArrayList<Integer>(l);
				cvList.add(new AbstractMap.SimpleEntry<List<Integer>, List<Integer>>(train, val));
			} else { // n-fold cv
				List<Integer> l = new ArrayList<Integer>();
				for (int i = 0; i < numSamples; i++)
					l.add(i);
				Collections.shuffle(l);
				int foldSize = numSamples / numFolds;
				for (int fold = 0; fold < numFolds; fold++) {
					List<Integer> val = new ArrayList<Integer>(l.subList(fold * foldSize, (fold + 1) * foldSize));
					List<Integer> train = new ArrayList<Integer>(l);
					train.removeAll(val);
					cvList.add(new AbstractMap.SimpleEntry<List<Integer>, List<Integer>>(train, val));
				}
			}
		}
		return cvList;
	}

	// Maybe we should remove this function due to triviality
	public static double getRMSE(List<double[]> response, List<double[]> desired) {
		return Math.sqrt(getMSE(response, desired));
	}

	// Mean sum of squares
	public static double getMSE(List<double[]> response, List<double[]> desired) {
		if (response.size() != desired.size())
			throw new RuntimeException("response.size() != desired.size()");

		double mse = 0;
		for (int i = 0; i < response.size(); i++)
			mse += Math.pow(response.get(i)[0] - desired.get(i)[0], 2);
		return mse / response.size();
	}

	public static double getR2(List<double[]> response, List<double[]> desired) {
		if (response.size() != desired.size())
			throw new RuntimeException();

		double ssRes = 0;
		for (int i = 0; i < response.size(); i++)
			ssRes += Math.pow(desired.get(i)[0] - response.get(i)[0], 2);

		double mean = 0;
		for (double[] d : desired)
			mean += d[0];
		mean /= desired.size();

		double ssTot = 0;
		for (int i = 0; i < desired.size(); i++)
			ssTot += Math.pow(desired.get(i)[0] - mean, 2);

		return 1.0 - ssRes / ssTot;
	}

	public static double getPearson(List<double[]> response, List<double[]> desired) {
		if (response.size() != desired.size())
			throw new RuntimeException();

		double meanDesired = 0;
		for (double[] d : desired)
			meanDesired += d[0];
		meanDesired /= desired.size();

		double meanResponse = 0;
		for (double[] d : response)
			meanResponse += d[0];
		meanResponse /= response.size();

		double a = 0;
		for (int i = 0; i < response.size(); i++)
			a += (response.get(i)[0] - meanResponse) * (desired.get(i)[0] - meanDesired);

		double b = 0;
		for (int i = 0; i < response.size(); i++)
			b += Math.pow(response.get(i)[0] - meanResponse, 2);
		b = Math.sqrt(b);

		double c = 0;
		for (int i = 0; i < desired.size(); i++)
			c += Math.pow(desired.get(i)[0] - meanDesired, 2);
		c = Math.sqrt(c);

		if (b == 0 || c == 0) // not sure about if this is ok
			return 0;

		return a / (b * c);
	}

	public static double getAIC(double mse, int nrParams, int nrSamples) {
		return nrSamples * Math.log(mse) + 2 * nrParams;
	}

	// FIXME only if model is univariate, linear and has norm-distributed residuals
	public static double getAICc(double mse, int nrParams, int nrSamples) {
		return getAIC(mse, nrParams, nrSamples) + (2 * nrParams * (nrParams + 1)) / (nrSamples - nrParams - 1);
	}

	public static double getBIC(double mse, int nrParams, int nrSamples) {
		return nrSamples * Math.log(mse) + nrParams * Math.log(nrSamples);
	}
}