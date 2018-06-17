package spawnn.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.log4j.Logger;
import org.geotools.data.DataStore;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureSource;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

import spawnn.dist.Dist;
import spawnn.utils.DataFrame.binding;

public class DataUtils {

	private static Logger log = Logger.getLogger(DataUtils.class);

	public static List<double[]> mappingToCluster(Map<double[], Set<double[]>> m) {
		List<double[]> r = new ArrayList<double[]>();

		List<double[]> k = new ArrayList<double[]>(m.keySet());
		for (int i = 0; i < k.size(); i++) {
			for (double[] d : m.get(k.get(i))) {
				double[] nd = Arrays.copyOf(d, d.length + 1);
				nd[nd.length - 1] = i;
				r.add(nd);
			}
		}
		return r;
	}

	// pretty sucks
	private static double sammonsStress(List<double[]> l1, Dist<double[]> a, List<double[]> l2, Dist<double[]> b) {
		double sum = 0;
		for (int i = 0; i < l1.size(); i++)
			for (int j = 0; j < i; j++)
				sum = a.dist(l1.get(i), l1.get(j));

		double left = 0;
		for (int i = 0; i < l1.size(); i++) {
			for (int j = 0; j < i; j++) {
				left += Math.pow(a.dist(l1.get(i), l1.get(j)) - b.dist(l2.get(i), l2.get(j)), 2) / a.dist(l1.get(i), l1.get(j));
			}
		}
		return (1.0 / sum) * left;
	}

	public static List<double[]> getSammonsProjection(List<double[]> samples, Dist<double[]> a, Dist<double[]> b, int dim) {
		List<double[]> projected = new ArrayList<double[]>();

		// init
		for (double[] d : samples)
			projected.add(Arrays.copyOf(d, dim));

		for (double lambda = 1; lambda >= 0.01; lambda *= 0.95) {

			for (int i = 0; i < samples.size(); i++) {
				for (int j = 0; j < samples.size(); j++) {
					if (i == j)
						continue;

					double dStar = a.dist(samples.get(i), samples.get(j));
					double d = b.dist(projected.get(i), projected.get(j));

					if (dStar == 0)
						dStar = 1e-10;

					double delta = lambda * (dStar - d) / dStar;
					for (int k = 0; k < dim; k++) {
						double correction = delta * (projected.get(i)[k] - projected.get(j)[k]);
						projected.get(i)[k] += correction;
						projected.get(j)[k] -= correction;
					}
					System.out.println(delta + ":" + dStar + ":" + d);

				}
			}
		}
		return projected;
	}

	public static double[][] transpose(double[][] tab) {
		double[][] r = new double[tab[0].length][tab.length];
		for (int i = 0; i < tab[0].length; i++)
			for (int j = 0; j < tab.length; j++)
				r[i][j] = tab[j][i];
		return r;
	}

	public static void writeTab(String fn, double[][] tab) {
		FileWriter fw = null;
		try {
			fw = new FileWriter(fn);
			for (int i = 0; i < tab.length; i++) {
				String s = "";
				for (int j = 0; j < tab[i].length; j++) {
					s += tab[i][j] + ",";
				}
				s = s.substring(0, s.length() - 1) + "\n";
				fw.write(s);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				fw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static double[][] readTab(String fn) {
		double[][] tab = null;
		int i = 0;
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(fn));
			String line = null;
			while ((line = br.readLine()) != null) {
				String[] s = line.split(",");

				if (tab == null)
					tab = new double[s.length][s.length]; // quadratic

				for (int j = 0; j < s.length; j++)
					tab[j][i] = Double.parseDouble(s[j]);
				i++;
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return tab;
	}

	public static int countCompactClusters(Collection<List<double[]>> cluster, List<Geometry> geoms, List<double[]> samples) {
		int sum = 0;
		GeometryFactory gf = new GeometryFactory();

		for (List<double[]> l : cluster) {
			if (l.size() == 0)
				continue;
			Geometry[] gs = new Geometry[l.size()];
			for (int i = 0; i < l.size(); i++)
				gs[i] = geoms.get(samples.indexOf(l.get(i)));
			GeometryCollection gc = gf.createGeometryCollection(gs);
			if (gc.union().getNumGeometries() == 1)
				sum++;
		}
		return sum;
	}

	public static int countNonEmptyCluster(Collection<List<double[]>> cluster) {
		int sum = 0;
		for (List<double[]> l : cluster)
			if (l.size() > 0)
				sum++;
		return sum;
	}

	public static double getPurity(Map<double[], Set<double[]>> cluster, Map<Integer, Set<double[]>> classes) {
		int sum = 0;
		int numSamples = 0;

		for (double[] w : cluster.keySet()) {
			numSamples += cluster.get(w).size();
			Map<Integer, Integer> classCount = new HashMap<Integer, Integer>();
			for (int i : classes.keySet())
				classCount.put(i, 0);

			for (double[] d : cluster.get(w)) {
				// get class
				for (int i : classes.keySet())
					if (classes.get(i).contains(d))
						classCount.put(i, classCount.get(i) + 1);
			}

			int max = 0;
			for (int cls : classCount.keySet()) {
				if (classCount.get(cls) > max)
					max = classCount.get(cls);
			}
			sum += max;
		}
		return (double) sum / numSamples;
	}

	public static long binCoeff(int n, int k) {
		long[] b = new long[n + 1];
		b[0] = 1;
		for (int i = 1; i <= n; i++) {
			b[i] = 1;
			for (int j = i - 1; j > 0; j--)
				b[j] += b[j - 1];
		}
		if (k > b.length - 1)
			return 0;
		else
			return b[k];
	}

	// does not work as exspected
	public static double getAdjustedRandIndex(List<List<double[]>> u, List<List<double[]>> v) {

		int[][] contTable = new int[u.size()][v.size()];
		for (int i = 0; i < u.size(); i++) { // zeile
			for (int j = 0; j < v.size(); j++) { // spalte
				Set<double[]> s = new HashSet<double[]>();
				s.addAll(u.get(i));
				s.addAll(v.get(j));
				contTable[i][j] = s.size();
			}
		}

		int[] aSums = new int[contTable.length];
		for (int i = 0; i < contTable.length; i++) { // zeile
			int a = 0;
			for (int j = 0; j < contTable[i].length; j++)
				// spalte
				a += contTable[i][j];
			aSums[i] = a;
		}

		int[] bSums = new int[contTable[0].length];
		for (int j = 0; j < contTable[0].length; j++) { // spalte
			int b = 0;
			for (int i = 0; i < contTable.length; i++)
				// zeile
				b += contTable[i][j];
			bSums[j] = b;
		}

		int n = 0;
		for (int i : aSums)
			n += i;

		double index = 0;
		for (int i = 0; i < contTable.length; i++)
			for (int j = 0; j < contTable[i].length; j++)
				index += binCoeff(contTable[i][j], 2);

		double coSumA = 0;
		for (int i = 0; i < aSums.length; i++)
			coSumA += binCoeff(aSums[i], 2);

		double coSumB = 0;
		for (int i = 0; i < bSums.length; i++)
			coSumB += binCoeff(bSums[i], 2);

		double exspectedIndex = (coSumA * coSumB) / binCoeff(n, 2);
		double maxIndex = 0.5 * (coSumA + coSumB);

		return (index - exspectedIndex) / (maxIndex - exspectedIndex);
	}

	public static double getDaviesBouldinIndex(Collection<Set<double[]>> clusters, Dist<double[]> dist) {
		double sum = 0;

		for (Set<double[]> c1 : clusters) {
			if (c1.isEmpty())
				continue;

			double[] c1Center = getMean(c1);

			double max = Double.MIN_VALUE;
			for (Set<double[]> c2 : clusters) {
				if (c2.isEmpty())
					continue;

				double[] c2Center = getMean(c2);

				if (c1.equals(c2))
					continue;

				double si = 0;
				for (double[] d : c1)
					si += dist.dist(d, c1Center);
				si /= c1.size();

				double sj = 0;
				for (double[] d : c2)
					sj += dist.dist(d, c2Center);
				sj /= c2.size();

				double r = (si + sj) / dist.dist(c1Center, c2Center);

				if (r > max)
					max = r;
			}
			sum += max;
		}
		return sum / clusters.size();
	}

	public static double[] getMean(Collection<double[]> c) {
		int l = c.iterator().next().length;
		double[] r = new double[l];
		for (double[] d : c)
			for (int i = 0; i < l; i++)
				r[i] += d[i];
		for (int i = 0; i < l; i++)
			r[i] /= c.size();
		return r;
	}

	public static RealMatrix toRealMatrix(List<double[]> samples) {
		double[][] m = new double[samples.size()][];
		for (int i = 0; i < samples.size(); i++)
			m[i] = samples.get(i);

		return new Array2DRowRealMatrix(m);
	}

	public static List<double[]> toSamples(RealMatrix rm) {
		List<double[]> l = new ArrayList<>();
		for (int i = 0; i < rm.getRowDimension(); i++)
			l.add(rm.getRow(i));
		return l;
	}

	public static double getSumOfSquares(Map<double[], Set<double[]>> clusters, Dist<double[]> dist) {
		double totalSum = 0;
		for (Entry<double[], Set<double[]>> e : clusters.entrySet())
			totalSum += getSumOfSquares(e.getKey(), e.getValue(), dist);
		return totalSum;
	}

	public static <T> double getSumOfSquares(T center, Set<T> s, Dist<T> dist) {
		double sum = 0;
		for (T d : s)
			sum += Math.pow(dist.dist(d, center), 2);
		return sum;
	}

	public static <T> double getMeanQuantizationError(Map<T, Set<T>> clusters, Dist<T> dist) {
		DescriptiveStatistics ds = new DescriptiveStatistics();
		for (Entry<T, Set<T>> e : clusters.entrySet())
			for (T d : e.getValue())
				ds.addValue(dist.dist(e.getKey(), d));
		return ds.getMean();
	}

	public static <T> double getQuantizationError(T centroid, Collection<T> data, Dist<T> dist) {
		double sum = 0;
		for (T d : data)
			sum += dist.dist(centroid, d);
		return sum / data.size();
	}

	public static void writeShape(List<double[]> samples, List<Geometry> geoms, String fn) {
		writeShape(samples, geoms, null, null, fn);
	}

	public static void writeShape(List<double[]> samples, List<Geometry> geoms, String[] names, String fn) {
		writeShape(samples, geoms, names, null, fn);
	}

	public static void writeShape(List<double[]> samples, List<Geometry> geoms, String[] names, CoordinateReferenceSystem crs, String fn) {
		if (names != null && samples.get(0).length != names.length)
			throw new RuntimeException("column-length does not match names-length: " + samples.get(0).length + "!=" + names.length);

		try {
			SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
			typeBuilder.setName("samples");
			for (int i = 0; i < samples.get(0).length; i++) {
				if (names != null) {
					String n = names[i];
					if (n.length() > 8) {
						n = n.substring(0, 8);
						// log.debug("Trunkating " + names[i] + " to " + n);
					}
					typeBuilder.add(n, Double.class);
				} else {
					typeBuilder.add("data" + i, Double.class);
				}
			}

			if (crs != null)
				typeBuilder.setCRS(crs);
			else
				log.warn("CRS not set!");

			Geometry g = geoms.get(0);
			if (g instanceof Polygon)
				typeBuilder.add("the_geom", Polygon.class);
			else if (g instanceof MultiPolygon)
				typeBuilder.add("the_geom", MultiPolygon.class);
			else if (g instanceof Point)
				typeBuilder.add("the_geom", Point.class);
			else if (g instanceof MultiPoint)
				typeBuilder.add("the_geom", MultiPoint.class);
			else
				throw new RuntimeException("Unkown geometry type!");

			SimpleFeatureType type = typeBuilder.buildFeatureType();
			SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type);

			ListFeatureCollection fc = new ListFeatureCollection(type);
			for (double[] d : samples) {
				for (int i = 0; i < d.length; i++)
					featureBuilder.add(d[i]);
				featureBuilder.add(geoms.get(samples.indexOf(d)));
				SimpleFeature sf = featureBuilder.buildFeature(null);
				fc.add(sf);
			}
			
			ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

			Map<String, Serializable> params = new HashMap<>();
			params.put("url", new File(fn).toURI().toURL());
			params.put("create spatial index", Boolean.TRUE);

			ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
			newDataStore.createSchema(fc.getSchema());

			String typeName = newDataStore.getTypeNames()[0];
			SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);
			SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

			Transaction transaction = new DefaultTransaction("create");
			try {
				featureStore.addFeatures(fc);
				transaction.commit();
			} catch (Exception e) {
				e.printStackTrace();
				transaction.rollback();
			} finally {
				transaction.close();
			}

		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Deprecated
	public static List<Geometry> readGeometriesFromShapeFile(File file) {
		return readShapedata(file, new int[] {}, false).geoms;
	}

	@Deprecated
	public static List<double[]> readSamplesFromShapeFile(File file, int[] ign, boolean printAttrs) {
		return readShapedata(file, ign, printAttrs).samples;
	}

	@Deprecated
	public static SpatialDataFrame readShapedata(File file, int[] ign, boolean printAttrs) {
		return readSpatialDataFrameFromShapefile(file, printAttrs);
	}

	public static List<Geometry> readCSVGeometries(InputStream is, int col) {
		List<Geometry> geoms = new ArrayList<Geometry>();

		BufferedReader reader = new BufferedReader(new InputStreamReader(is));

		WKTReader wr = new WKTReader();

		try {
			// header handling
			String header = null;
			while ((header = reader.readLine()) != null)
				// skip comments
				if (!header.startsWith("#")) // header found
					break;

			String line = null;
			while ((line = reader.readLine()) != null) {

				if (line.startsWith("#"))
					continue;

				String g = line.split(",")[col];
				geoms.add(wr.read(g));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}

		return geoms;
	}

	@Deprecated
	public static List<double[]> readCSV(String file, int[] ign) {
		try {
			return readCSV(new FileInputStream(file), ign);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Deprecated
	public static List<double[]> readCSV(String file) {
		return readCSV(file, new int[] {});
	}

	@Deprecated
	public static List<double[]> readCSV(InputStream is) {
		return readCSV(is, new int[] {});
	}

	@Deprecated
	public static List<double[]> readCSV(InputStream is, int[] ign) {
		SpatialDataFrame sd = new SpatialDataFrame(); // actually, its not spatial data
		List<double[]> r = new ArrayList<double[]>();

		Set<Integer> ignore = new HashSet<Integer>();
		for (int i : ign)
			ignore.add(i);

		BufferedReader reader = new BufferedReader(new InputStreamReader(is));

		try {
			// header handling
			String header = null;
			while ((header = reader.readLine()) != null)
				// skip comments
				if (!header.startsWith("#"))
					break;

			List<String> names = new ArrayList<String>();
			String[] h = header.split(",");

			for (int i = 0; i < h.length; i++) {
				if (!ignore.contains(i))
					names.add(h[i]);
			}
			sd.names = names;

			String line = null;
			while ((line = reader.readLine()) != null) {

				if (line.startsWith("#"))
					continue;

				String[] data = line.split(",");
				double[] d = new double[data.length - ign.length];
				int modIdx = 0;
				for (int i = 0; i < data.length; i++)
					if (ignore.contains(i))
						modIdx++;
					else
						d[i - modIdx] = Double.parseDouble(data[i]);
				r.add(d);

			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		sd.samples = r;

		return sd.samples;
	}

	public static void writeCSV(OutputStream os, List<double[]> samples) {
		writeCSV(os, samples, null, ',');
	}

	public static void writeCSV(String fn, Collection<double[]> samples, String[] names) {
		writeCSV(fn, samples, names, ',');
	}

	public static void writeCSV(String fn, Collection<double[]> samples, String[] names, char sep) {
		try {
			writeCSV(new FileOutputStream(fn), samples, names, sep);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public static void writeCSV(OutputStream os, Collection<double[]> samples, String[] names, char sep) {
		try {
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));

			if (names != null) {
				String header = "";
				for (int i = 0; i < names.length; i++)
					header += names[i] + sep;
				header = header.substring(0, header.length() - 1);
				writer.write(header + "\n");
			} else {
				String header = "";
				for (int i = 0; i < samples.iterator().next().length; i++)
					header += "att_" + i + sep;
				header = header.substring(0, header.length() - 1);
				writer.write(header + "\n");
			}

			// not really nice
			Map<Integer, Boolean> typeMap = new HashMap<Integer, Boolean>();
			for (int i = 0; i < samples.iterator().next().length; i++) {
				boolean integer = true;
				for (double[] d : samples)
					if ((d[i] != Math.floor(d[i])) || Double.isInfinite(d[i]))
						integer = false;
				typeMap.put(i, integer);
			}

			for (double[] d : samples) {
				String s = "";
				for (int i = 0; i < d.length; i++)
					if (typeMap.get(i))
						s += (int) d[i] + "" + sep;
					else
						s += d[i] + "" + sep;
				s = s.substring(0, s.length() - 1);
				writer.write(s + "\n");
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static DataFrame readDataFrameFromCSV(String file, int[] ign, boolean verbose) {
		return readDataFrameFromCSV(new File(file), ign, verbose);
	}

	public static DataFrame readDataFrameFromCSV(File file, int[] ign, boolean verbose) {
		return readDataFrameFromCSV(file, ign, verbose, ',');
	}

	public static DataFrame readDataFrameFromCSV(File file, int[] ign, boolean verbose, char sep) {
		char quote = '"';

		Set<Integer> ignore = new HashSet<Integer>();
		for (int i : ign)
			ignore.add(i);

		DataFrame sd = new DataFrame();
		while (true) {
			boolean retry = false;
			List<double[]> r = new ArrayList<double[]>();
			BufferedReader reader = null;
			try {
				reader = new BufferedReader(new FileReader(file));
				// header handling
				String header = null;
				while ((header = reader.readLine()) != null)
					// skip comments
					if (!header.startsWith("#"))
						break;

				// clear line from commas within strings
				{
					boolean start = false;
					char[] charStr = header.toCharArray();
					for (int i = 0; i < charStr.length; i++) {
						if (charStr[i] == quote)
							start = !start;
						if (start == true && charStr[i] == sep)
							charStr[i] = ';'; // replace
					}
					header = String.valueOf(charStr);
				}
				String[] h = header.split(sep + "");

				List<String> names = new ArrayList<String>();
				for (int i = 0; i < h.length; i++)
					if (!ignore.contains(i))
						names.add(h[i]);
				sd.names = names;

				String line = null;
				while ((line = reader.readLine()) != null) {

					if (line.startsWith("#"))
						continue;

					// clear line from commas within strings
					boolean start = false;
					char[] charStr = line.toCharArray();
					for (int i = 0; i < charStr.length; i++) {
						if (charStr[i] == quote)
							start = !start;
						if (start == true && charStr[i] == sep)
							charStr[i] = ';'; // replace
					}
					line = String.valueOf(charStr);

					String[] data = line.split(sep + "");
					double[] d = new double[data.length - ignore.size()];
					int modIdx = 0;
					for (int i = 0; i < data.length; i++)
						if (ignore.contains(i))
							modIdx++;
						else {
							try {
								d[i - modIdx] = Double.parseDouble(data[i]);
							} catch (NumberFormatException e) {
								log.warn("Cannot parse value " + data[i] + " in column " + i + ", ignoring column...");
								ignore.add(i);
								retry = true;
								break;
							}
						}
					r.add(d);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (reader != null)
					try {
						reader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
			}

			sd.samples = r;
			if (!retry)
				break;
		}

		// TODO for now its all double
		sd.bindings = new ArrayList<binding>();
		for (int i = 0; i < sd.names.size(); i++)
			sd.bindings.add(binding.Double);

		return sd;
	}

	// FIXME verbose has no effect
	public static SpatialDataFrame readSpatialDataFrameFromCSV(File file, int[] ga, int[] ign, boolean verbose) {
		GeometryFactory gf = new GeometryFactory();

		SpatialDataFrame sd = new SpatialDataFrame();
		sd.samples = new ArrayList<double[]>();
		sd.geoms = new ArrayList<Geometry>();
		sd.bindings = new ArrayList<SpatialDataFrame.binding>();

		Set<Integer> ignore = new HashSet<Integer>();
		for (int i : ign)
			ignore.add(i);

		BufferedReader reader = null;

		try {
			reader = new BufferedReader(new FileReader(file));
			// header handling
			String header = null;
			while ((header = reader.readLine()) != null)
				// skip comments
				if (!header.startsWith("#"))
					break;

			List<String> names = new ArrayList<String>();
			String[] h = header.split(",");

			for (int i = 0; i < h.length; i++) {
				if (!ignore.contains(i)) {
					log.debug(i + "," + h[i] + "," + names.size());
					names.add(h[i]);
				}
			}
			sd.names = names;

			String line = null;
			while ((line = reader.readLine()) != null) {

				if (line.startsWith("#"))
					continue;

				String[] data = line.split(",");
				double[] d = new double[data.length - ign.length];
				int modIdx = 0;
				for (int i = 0; i < data.length; i++)
					if (ignore.contains(i))
						modIdx++;
					else
						d[i - modIdx] = Double.parseDouble(data[i]);

				sd.samples.add(d);

				Coordinate c;
				if (ga.length == 2) {
					c = new Coordinate(Double.parseDouble(data[ga[0]]), Double.parseDouble(data[ga[1]]));
				} else if (ga.length == 3) {
					c = new Coordinate(Double.parseDouble(data[ga[0]]), Double.parseDouble(data[ga[1]]), Double.parseDouble(data[ga[2]]));
				} else {
					throw new RuntimeException("Cannot create point of length " + ga.length);
				}
				sd.geoms.add(gf.createPoint(c));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (reader != null)
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}

		// TODO for now its all double
		sd.bindings = new ArrayList<binding>();
		for (int i = 0; i < sd.names.size(); i++)
			sd.bindings.add(binding.Double);

		return sd;
	}

	public static SpatialDataFrame readSpatialDataFrameFromShapefile(File file, boolean debug) {
		return readSpatialDataFrameFromShapefile(file, new int[] {}, debug);
	}

	public static SpatialDataFrame readSpatialDataFrameFromShapefile(File file, int[] toDouble, boolean debug) {

		SpatialDataFrame sd = new SpatialDataFrame();
		sd.samples = new ArrayList<double[]>();
		sd.geoms = new ArrayList<Geometry>();
		sd.names = new ArrayList<String>();
		sd.bindings = new ArrayList<SpatialDataFrame.binding>();

		DataStore dataStore = null;
		try {
			dataStore = new ShapefileDataStore((file).toURI().toURL());
			FeatureSource<SimpleFeatureType, SimpleFeature> featureSource = dataStore.getFeatureSource(dataStore.getTypeNames()[0]);
			sd.crs = featureSource.getSchema().getCoordinateReferenceSystem();

			Set<Integer> td = new HashSet<>();
			for (int i : toDouble)
				td.add(i);
			Set<Integer> ignore = new HashSet<Integer>();

			List<AttributeDescriptor> adl = featureSource.getFeatures().getSchema().getAttributeDescriptors(); // all
			for (int i = 0; i < adl.size(); i++) {
				AttributeDescriptor ad = adl.get(i);
				String bin = ad.getType().getBinding().getName();

				if (ignore.contains(i))
					continue;

				if (bin.equals("java.lang.Integer")) {
					sd.names.add(ad.getLocalName());
					sd.bindings.add(SpatialDataFrame.binding.Integer);
				} else if (bin.equals("java.lang.Double") || td.contains(i)) {
					sd.names.add(ad.getLocalName());
					sd.bindings.add(SpatialDataFrame.binding.Double);
				} else if (bin.equals("java.lang.Long")) {
					sd.names.add(ad.getLocalName());
					sd.bindings.add(SpatialDataFrame.binding.Long);
				} else {
					ignore.add(i);
					if (debug)
						log.debug("Ignoring " + ad.getLocalName() + ", because " + bin);
				}
			}

			if (debug) {
				int idx = 0;
				for (int i = 0; i < adl.size(); i++) {
					if (ignore.contains(i))
						log.debug(i + ":" + adl.get(i).getLocalName() + ", IGN");
					else
						log.debug(i + ":" + adl.get(i).getLocalName() + "," + (idx++));
				}
			}

			FeatureIterator<SimpleFeature> it = featureSource.getFeatures().features();
			try {
				while (it.hasNext()) {
					SimpleFeature feature = it.next();
					double[] d = new double[adl.size() - ignore.size()];

					int idx = 0;
					for (int i = 0; i < adl.size(); i++) {

						if (ignore.contains(i))
							continue;

						String name = adl.get(i).getLocalName();
						Object o = feature.getAttribute(name);
						if (td.contains(i) && o instanceof String)
							d[idx++] = Double.parseDouble((String) o);
						else if (o instanceof Double)
							d[idx++] = ((Double) o).doubleValue();
						else if (o instanceof Integer)
							d[idx++] = ((Integer) o).intValue();
						else if (o instanceof Long)
							d[idx++] = ((Long) o).longValue();
						else {
							log.error("Unknown attribute type: " + name + ", " + o + ", " + i);
							System.exit(1);
						}
					}

					sd.samples.add(d);
					sd.geoms.add((Geometry) feature.getDefaultGeometry());
				}
			} finally {
				if (it != null) {
					it.close();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			dataStore.dispose();
		}
		return sd;
	}

	@Deprecated
	public static void normalize(List<double[]> samples) {
		int length = samples.get(0).length;
		for (int i = 0; i < length; i++)
			normalizeColumn(samples, i);
	}

	@Deprecated
	public static void normalizeColumn(List<double[]> samples, int idx) {
		SummaryStatistics ss = new SummaryStatistics();
		for (double[] d : samples)
			ss.addValue(d[idx]);

		for (double[] d : samples)
			d[idx] = (d[idx] - ss.getMin()) / (ss.getMax() - ss.getMin()); // 0...1
	}

	@Deprecated
	public static void normalizeColumns(List<double[]> samples, int[] idx) {
		for (int i : idx)
			normalizeColumn(samples, i);
	}

	public static void normalizeGeoColumns(List<double[]> samples, int[] idx) {
		double[] min = new double[idx.length], max = new double[idx.length];
		for (int i = 0; i < idx.length; i++) {
			min[i] = Double.MAX_VALUE;
			max[i] = Double.MIN_VALUE;
		}

		for (double[] d : samples) {
			for (int i = 0; i < idx.length; i++) {
				min[i] = Math.min(min[i], d[idx[i]]);
				max[i] = Math.max(max[i], d[idx[i]]);
			}
		}

		double l = Double.MIN_VALUE;
		for (int i = 0; i < idx.length; i++)
			l = Math.max(l, max[i] - min[i]);

		for (double[] d : samples)
			for (int i = 0; i < idx.length; i++)
				d[idx[i]] = (d[idx[i]] - min[i]) / l;
	}

	@Deprecated
	public static void zScore(List<double[]> samples) {
		int length = samples.get(0).length;
		for (int i = 0; i < length; i++)
			zScoreColumn(samples, i);
	}

	@Deprecated
	public static void zScoreColumn(List<double[]> samples, int idx) {
		SummaryStatistics ss = new SummaryStatistics();
		for (double[] d : samples)
			ss.addValue(d[idx]);

		for (double[] d : samples)
			d[idx] = (d[idx] - ss.getMean()) / ss.getStandardDeviation();
	}
	
	public static void zScoreColumns(List<double[]> samplesTrain, List<double[]> samplesTest, int[] fa ) {
		for( int idx : fa ) {
			SummaryStatistics ss = new SummaryStatistics();
			for (double[] d : samplesTrain)
				ss.addValue(d[idx]);
			
			double mean = ss.getMean();
			double sd = ss.getStandardDeviation();
			for (double[] d : samplesTrain)
				d[idx] = (d[idx] - mean) / sd;
			for (double[] d : samplesTest)
				d[idx] = (d[idx] - mean) / sd;
		}
	}

	@Deprecated
	public static void zScoreColumns(List<double[]> samples) {
		int length = samples.get(0).length;
		for (int i = 0; i < length; i++)
			zScoreColumn(samples, i);
	}

	@Deprecated
	public static void zScoreColumns(List<double[]> samples, int[] idx) {
		for (int i : idx)
			zScoreColumn(samples, i);
	}

	public enum Transform {
		pow2, log, sqrt, div, zScore, scale01, pca
	};

	public static void transform(List<double[]> samples, int[] fa, Transform t) {
		SummaryStatistics[] ds = new SummaryStatistics[fa.length];
		for (int i = 0; i < fa.length; i++)
			ds[i] = new SummaryStatistics();
		for (double[] d : samples)
			for (int i = 0; i < fa.length; i++)
				ds[i].addValue(d[fa[i]]);

		RealMatrix v = null;
		if (t == Transform.pca) {
			RealMatrix matrix = new Array2DRowRealMatrix(samples.size(), fa.length);
			for (int i = 0; i < samples.size(); i++)
				for (int j = 0; j < fa.length; j++)
					matrix.setEntry(i, j, samples.get(i)[fa[j]]);

			SingularValueDecomposition svd = new SingularValueDecomposition(matrix);
			v = svd.getU().multiply(svd.getS());
		}

		for (int i = 0; i < samples.size(); i++) {
			double[] d = samples.get(i);
			for (int j = 0; j < fa.length; j++) {
				if (t == Transform.log)
					d[fa[j]] = Math.log(d[fa[j]]);
				else if (t == Transform.pow2)
					d[fa[j]] = Math.pow(d[fa[j]], 2);
				else if (t == Transform.sqrt)
					d[fa[j]] = Math.sqrt(d[fa[j]]);
				else if (t == Transform.div)
					d[fa[j]] = 1.0 / d[fa[j]];
				else if (t == Transform.zScore)
					d[fa[j]] = (d[fa[j]] - ds[j].getMean()) / ds[j].getStandardDeviation();
				else if (t == Transform.scale01)
					d[fa[j]] = (d[fa[j]] - ds[j].getMin()) / (ds[j].getMax() - ds[j].getMin());
				else if (t == Transform.pca)
					d[fa[j]] = v.getEntry(i, j);
			}
		}
	}

	public static void transform(List<double[]> samples, Transform t) {
		int[] fa = new int[samples.get(0).length];
		for (int i = 0; i < fa.length; i++)
			fa[i] = i;
		transform(samples, fa, t);
	}

	public static void zScoreGeoColumns(List<double[]> samples, int[] idx, Dist<double[]> dist) {
		double[] mean = new double[samples.get(0).length];
		for (double[] s : samples)
			for (int i : idx)
				mean[i] += s[i] / samples.size();

		double stdDev = 0;
		for (double[] s : samples)
			stdDev += Math.pow(dist.dist(s, mean), 2);
		stdDev = Math.sqrt(stdDev / (samples.size() - 1));

		for (double[] s : samples)
			for (int i : idx)
				s[i] = (s[i] - mean[i]) / stdDev;
	}

	public static List<double[]> removeColumns(List<double[]> samples, int[] ign) {
		List<double[]> ns = new ArrayList<double[]>();

		Set<Integer> ignore = new HashSet<Integer>();
		for (int i : ign)
			ignore.add(i);

		for (double[] x : samples) {
			double[] n = new double[x.length - ign.length];

			int mod = 0;
			for (int i = 0; i < x.length; i++) {
				if (ignore.contains(i) || i >= n.length)
					mod++;
				else
					n[i - mod] = x[i];
			}
			ns.add(n);
		}
		return ns;
	}

	public static List<double[]> retainColumns(List<double[]> samples, int[] r) {
		int n = samples.get(0).length;

		// build list from array
		List<Integer> ret = new ArrayList<Integer>();
		for (int i : r)
			ret.add(i);

		// build all list
		List<Integer> idx = new ArrayList<Integer>();
		for (int i = 0; i < n; i++)
			idx.add(i);

		// remove wanted from all-list
		idx.removeAll(ret);

		// build array
		int[] a = new int[idx.size()];
		for (int i = 0; i < idx.size(); i++)
			a[i] = idx.get(i);

		// remove collums of all-,list
		return removeColumns(samples, a);
	}

	public static <T> Map<T, Map<T, Double>> readDistMatrixSquare(List<T> samples, File fn) {
		Map<T, Map<T, Double>> distMatrix = new HashMap<T, Map<T, Double>>();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(fn));
			String line = null;
			int lineIdx = 0;
			while ((line = br.readLine()) != null) {
				Map<T, Double> m = new HashMap<T, Double>();

				String[] s = line.split(",");
				for (int i = 0; i < s.length; i++)
					if (!s[i].isEmpty()) {
						double d = Double.parseDouble(s[i]);
						m.put(samples.get(i), d);
					}
				distMatrix.put(samples.get(lineIdx++), m);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return distMatrix;
	}

	public static double getSumOfSquares(Collection<double[]> s, Dist<double[]> dist) {
		double ssq = 0;
		if (s.isEmpty())
			return ssq;

		double[] mean = getMean(s);
		for (double[] d : s) {
			double di = dist.dist(mean, d);
			ssq += di * di;
		}

		return ssq;
	}

	public static double getWithinSumOfSquares(Collection<Set<double[]>> c, Dist<double[]> dist) {
		double ssq = 0;
		for (Set<double[]> s : c)
			ssq += getSumOfSquares(s, dist);
		return ssq;
	}

	// If one wants to perform PCA on a correlation matrix (instead of a covariance matrix), then columns of X should not only be centered, but standardized as well, i.e. divided by their standard deviations.
	public static List<double[]> reduceDimensionByPCA(List<double[]> samples, int nrComponents) {
		RealMatrix matrix = new Array2DRowRealMatrix(samples.size(), samples.get(0).length);
		for (int i = 0; i < samples.size(); i++)
			matrix.setRow(i, samples.get(i));

		SingularValueDecomposition svd = new SingularValueDecomposition(matrix);
		RealMatrix v = svd.getU().multiply(svd.getS());

		List<double[]> ns = new ArrayList<double[]>();
		for (int i = 0; i < v.getRowDimension(); i++) {
			double[] d = Arrays.copyOf(v.getRow(i), nrComponents);
			ns.add(d);
		}
		return ns;
	}

	public static void main(String[] args) {
		DataFrame df = DataUtils.readDataFrameFromCSV("data/iris.csv", new int[] {}, true);

		DataUtils.transform(df.samples, Transform.zScore);
		DataUtils.transform(df.samples, Transform.pca);

		for (double[] d : df.samples)
			log.debug(Arrays.toString(d));
	}

	public static double[] strip(double[] d, int[] fa) {
		double[] nd = new double[fa.length];
		for (int j = 0; j < fa.length; j++)
			nd[j] = d[fa[j]];
		return nd;
	}
}
