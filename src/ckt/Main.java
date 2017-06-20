package ckt;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Properties;
import java.util.function.Predicate;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import ckt.KTParameters.Gaussian;

public class Main
{
	/** The Precision for aggregated metrics. */
	static double aggregatedPrecision;
	/** Constant to add to the metrics aggregation. */
	static double aggregationBase;
	/** Stores all the Sequences used for cross validation. */
	static ArrayList<Sequence> allSequences;
	/** Stores the Sequences used as learning set. */
	static ArrayList<Sequence> learningSet;
	static ArrayList<String> log = new ArrayList<String>();
	/** The parameters for main Knowledge. */
	static KTParameters[] mainParameters;
	/** The name of the metrics available. */
	static ArrayList<Metric> metrics;
	/** Settings from settings.properties */
	static Properties settings;
	/** Stores the Sequences used as testing set. */
	static ArrayList<Sequence> testingSet;
	/** The number of Sequences to use as testing set for each step in cross validation. */
	static int testingSize;
	/** The total number of problems. */
	static int totalProblems;
	/** The number of steps in cross validation. */
	static int validations;

	/** Aggregates Knowledge found for each metric.
	 * 
	 * @return True if succeeded. */
	private static boolean aggregateMetrics()
	{
		log("Aggregating metrics...");
		if (settings.getProperty("aggregation_type").equals("svm"))
		{
			// Applying SVM script
			exportData(new File("resultat.csv"), outputSVM());
			if (!new File(settings.getProperty("aggregation_value")).exists())
			{
				System.out.println("SVM file not found");
				return false;
			}
			try
			{
				Runtime.getRuntime().exec("python " + settings.getProperty("aggregation_value"));
			} catch (IOException e)
			{
				e.printStackTrace();
				return false;
			}

			// Gathering SVM data
			String weightsData = Utils.readTextFile("weights.txt");
			weightsData = weightsData.replaceAll("\\n", "").replaceAll("\\[", "").replaceAll("\\]", "");
			while (weightsData.contains("  "))
				weightsData = weightsData.replaceAll("  ", " ");
			String[] weights = weightsData.split(" ");
			for (int i = 1; i < weights.length; ++i)
				metrics.get(i - 1).weight = Double.parseDouble(weights[i]);

			// Aggregating
			double[] aggregated = new double[Sequence.DRAWS];
			for (Sequence sequence : allSequences)
				for (Problem problem : sequence.problems)
				{
					for (int d = 0; d < Sequence.DRAWS; ++d)
					{
						aggregated[d] = 0;
						for (Metric metric : metrics)
							aggregated[d] += metric.weight * problem.metricKnowledge.get(metric).next();
					}
					problem.aggregatedKnowledge = Utils.makeGaussian(aggregated);
				}
		} else
		{
			ScriptEngine script = new ScriptEngineManager().getEngineByName("nashorn");
			File file = null;
			if (settings.getProperty("aggregation_type").equals("script")) file = new File(settings.getProperty("aggregation_value"));

			int invalid = 0;
			double[] scores = new double[metrics.size()];
			double[] aggregated = new double[Sequence.DRAWS];
			for (Sequence sequence : allSequences)
				for (Problem problem : sequence.problems)
				{
					for (int d = 0; d < Sequence.DRAWS; ++d)
					{
						if (file == null)
						{
							aggregated[d] = aggregationBase;
							// if (allSequences.indexOf(sequence) <= 10 && d == 0) log();
							for (Metric metric : metrics)
							{
								/* if (allSequences.indexOf(sequence) <= 10 && d == 0) log(metric.name + ": " + metric.weight + " * " + metric.initialDistribution.unreduce(sequence.finalProblem().metricKnowledge.get(metric).next())); */
								aggregated[d] += metric.weight * metric.initialDistribution.unreduce(problem.metricKnowledge.get(metric).next());
							}
						} else try
						{
							for (int m = 0; m < metrics.size(); ++m)
								scores[m] = metrics.get(m).initialDistribution.unreduce(problem.metricKnowledge.get(metrics.get(m)).next());

							// Input: double[] containing scores for each metric for a single problem.
							// Output: aggregated score as a double.
							script.put("metrics", scores);
							script.eval(new BufferedReader(new FileReader(file)));
							aggregated[d] = (double) script.get("aggregated");
						} catch (FileNotFoundException e)
						{
							log("Couldn't find aggregation script: " + settings.getProperty("aggregation_script"));
							return false;
						} catch (Exception e)
						{
							log("Error while aggregating metrics:\n" + e.getMessage());
							return false;
						}
						if (aggregated[d] >= 1) aggregated[d] = 1;
					}
					problem.aggregatedKnowledge = Utils.makeGaussian(aggregated);
					if (problem.aggregatedKnowledge.mean > 1) ++invalid;

				}
			if (invalid != 0) log("Found " + invalid + " invalid knowledge values!");
		}

		return true;
	}

	private static void applyKnowledgeTracing(Metric metric)
	{
		if (metric == null) log("Executing Knowledge Tracing...");
		else log("Executing Knowledge Tracing on metric \"" + metric.name + "\"...");
		if (metric != null) applyThreshold(metric);
		findKnowledgeSequences();

		KTParameters[] params = new KTParameters[validations + 2];
		if (metric == null) mainParameters = params;
		else params = mainParameters;

		for (int i = 0; i < validations; ++i)
		{
			learningSet.clear();
			testingSet.clear();
			for (int j = 0; j < allSequences.size(); ++j)
				if (j < i * testingSize || j >= (i + 1) * testingSize) learningSet.add(allSequences.get(j));
				else testingSet.add(allSequences.get(j));

			params[i] = computeParameters();
			computeKnowledge(params[i], metric);
		}
		computeStats(params);
	}

	/** Uses the input <code>threshold</code> to determine the correctness of the problem. */
	private static void applyThreshold(double threshold)
	{
		for (Sequence sequence : allSequences)
			for (Problem problem : sequence.problems)
				problem.isCorrect = problem.score >= threshold;
	}

	/** Uses the metric's <code>threshold</code> to determine the correctness of the problem. */
	private static void applyThreshold(Metric metric)
	{
		for (Sequence sequence : allSequences)
			for (Problem problem : sequence.problems)
				problem.isCorrect = (!metric.thresholdReversed && problem.metricScores.get(metric) >= metric.threshold)
						|| (metric.thresholdReversed && problem.metricScores.get(metric) < metric.threshold);
	}

	/** 1) Removes empty sequences.<br />
	 * 2) Sorts problems in sequences chronologically.<br />
	 * 3) Reduces and centers problem scores between 0 and 1 if <code>center</code> is true. 4) Reduces and centers metric scores between 0 and 1 if <code>centerMetrics</code> is true. */
	static void cleanSequences(boolean center, boolean centerMetrics)
	{
		allSequences.removeIf(new Predicate<Sequence>()
		{
			@Override
			public boolean test(Sequence t)
			{
				if (t.problems.size() == 0) log("Sequence " + t.name + " has no problems !");
				return t.problems.size() == 0;
			}
		});
		allSequences.sort(Comparator.naturalOrder());

		totalProblems = 0;
		for (Sequence sequence : allSequences)
			totalProblems += sequence.problems.size();

		if (center)
		{
			double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
			for (Sequence sequence : allSequences)
				for (Problem problem : sequence.problems)
					if (problem.score < min) min = problem.score;
					else if (problem.score > max) max = problem.score;

			for (Sequence sequence : allSequences)
				for (Problem problem : sequence.problems)
					problem.score = (problem.score - min) / (max - min);
		}

		if (metrics.size() != 0 && centerMetrics)
		{
			for (Metric metric : metrics)
			{
				double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
				for (Sequence sequence : allSequences)
					for (Problem problem : sequence.problems)
					{
						double score = problem.metricScores.get(metric);
						if (score < min) min = score;
						else if (score > max) max = score;
					}

				for (Sequence sequence : allSequences)
					for (Problem problem : sequence.problems)
						problem.metricScores.put(metric, (problem.metricScores.get(metric) - min) / (max - min));

				metric.threshold = (metric.threshold - min) / (max - min);
				metric.initialDistribution = new Gaussian(min, max - min);
			}
		}
	}

	/** Computes the Knowledge of the Sequences. */
	private static void computeKnowledge(KTParameters parameters, Metric metric)
	{
		for (Sequence sequence : testingSet)
			sequence.computeKnowledge(parameters, metric);
	}

	/** Determines P(L0), P(T), P(G), P(S) */
	private static KTParameters computeParameters()
	{
		double kStart = 0, mTransition = 0, mGuess = 0, mSlip = 0;
		int s = 0;

		// P(L0)
		int tSize = 0, gSize = 0, sSize = 0;
		for (Sequence sequence : learningSet)
		{
			kStart += sequence.knowledgeSequence.get(0);
			++s;
			/* if (sequence.knowledgeSequence.size() > 1) { kStart += sequence.knowledgeSequence.get(1); ++s; } if (sequence.knowledgeSequence.size() > 2) { kStart += sequence.knowledgeSequence.get(2); ++s; } */
		}
		kStart /= s;

		// mu(P(T)), mu(P(G)), mu(P(S))
		for (Sequence sequence : learningSet)
		{
			sequence.computeProbabilities(kStart);
			if (!Double.isNaN(sequence.parameters.transition))
			{
				mTransition += sequence.parameters.transition;
				++tSize;
			}
			if (!Double.isNaN(sequence.parameters.guess.mean))
			{
				mGuess += sequence.parameters.guess.next();
				++gSize;
			}
			if (!Double.isNaN(sequence.parameters.slip.mean))
			{
				mSlip += sequence.parameters.slip.next();
				++sSize;
			}
		}
		mTransition /= tSize;
		mGuess /= gSize;
		mSlip /= sSize;

		// sigma(P(G)), sigma(P(S))
		// double sTransition = 0;
		double sGuess = 0, sSlip = 0;
		for (Sequence sequence : learningSet)
		{
			// if (!Double.isNaN(sequence.parameters.transition)) sTransition += Math.pow(sequence.parameters.transition - mTransition, 2);
			if (!Double.isNaN(sequence.parameters.guess.mean)) sGuess += Math.pow(sequence.parameters.guess.mean - mGuess, 2);
			if (!Double.isNaN(sequence.parameters.slip.mean)) sSlip += Math.pow(sequence.parameters.slip.mean - mSlip, 2);
		}
		// sTransition = Math.sqrt(sTransition / tSize);
		sGuess = Math.sqrt(sGuess / gSize);
		sSlip = Math.sqrt(sSlip / sSize);

		// Can happen if threshold is too low or too high
		if (Double.isNaN(mGuess)) mGuess = 0;
		if (Double.isNaN(sGuess)) sGuess = 0;
		if (Double.isNaN(mSlip)) mSlip = 0;
		if (Double.isNaN(sSlip)) sSlip = 0;

		return new KTParameters(kStart, mTransition, new Gaussian(mGuess, sGuess), new Gaussian(mSlip, sSlip));
	}

	/** @return The Precision for the Knowledge for the input sequences.
	 * @param aggregated - If true, will compute the Precision for the aggregated Knowledge. */
	private static double computePrecision(ArrayList<Sequence> sequences, boolean aggregated)
	{
		double precision = 0;
		int count = 0;
		for (Sequence sequence : sequences)
			for (Problem problem : sequence.problems)
				if (problem.isRepresentative)
				{
					++count;
					precision += Math.pow(sequence.finalProblem().expectedKnowledge - (aggregated ? problem.aggregatedKnowledge : problem.knowledge).mean, 2);
				}

		return Math.sqrt(precision / count);
	}

	/** Uses the metrics to calculate the score of each problem.
	 * 
	 * @return True if it succeeded. */
	private static boolean computeScores()
	{
		log("Calculating scores...");
		ScriptEngine script = new ScriptEngineManager().getEngineByName("nashorn");
		File file = null;
		if (settings.getProperty("aggregation_type").equals("script")) file = new File(settings.getProperty("aggregation_value"));

		double[] scores = new double[metrics.size()];
		for (Sequence sequence : allSequences)
		{
			for (Problem problem : sequence.problems)
			{
				if (file == null)
				{
					problem.score = aggregationBase;
					// if (allSequences.indexOf(sequence) <= 10 && d == 0) log();
					for (Metric metric : metrics)
						problem.score += metric.weight * problem.metricScores.get(metric);

				} else try
				{
					for (int m = 0; m < metrics.size(); ++m)
						scores[m] = problem.metricScores.get(metrics.get(m));

					// Input: double[] containing scores for each metric for a single problem.
					// Output: aggregated score as a double.
					script.put("metrics", scores);
					script.eval(new BufferedReader(new FileReader(file)));
					problem.score = (double) script.get("aggregated");
				} catch (FileNotFoundException e)
				{
					log("Couldn't find aggregation script: " + settings.getProperty("aggregation_script"));
					return false;
				} catch (Exception e)
				{
					log("Error while aggregating metrics:\n" + e.getMessage());
					return false;
				}
			}

		}

		return true;
	}

	/** Computes statistics for the parameters. */
	private static void computeStats(KTParameters[] parametersArray)
	{
		// Mean
		double start = 0, transition = 0, guess = 0, slip = 0;
		for (int i = 0; i < parametersArray.length - 2; ++i)
		{
			start += parametersArray[i].startKnowledge;
			transition += parametersArray[i].transition;
			guess += parametersArray[i].guess.mean;
			slip += parametersArray[i].slip.mean;
		}
		start /= parametersArray.length;
		transition /= parametersArray.length;
		guess /= parametersArray.length;
		slip /= parametersArray.length;

		// Variation
		double sStart = 0, sTransition = 0, sGuess = 0, sSlip = 0;
		for (int i = 0; i < parametersArray.length - 2; ++i)
		{
			sStart += Math.pow(parametersArray[i].startKnowledge - start, 2);
			sTransition += Math.pow(parametersArray[i].transition - transition, 2);
			sGuess += Math.pow(parametersArray[i].guess.mean, 2) + Math.pow(parametersArray[i].guess.variation, 2);
			sSlip += Math.pow(parametersArray[i].slip.mean, 2) + Math.pow(parametersArray[i].slip.variation, 2);
		}
		sStart = Math.sqrt(sStart / parametersArray.length);
		sTransition = Math.sqrt(sTransition / parametersArray.length);
		sGuess = Math.sqrt(sGuess / parametersArray.length - Math.pow(guess, 2));
		sSlip = Math.sqrt(sSlip / parametersArray.length - Math.pow(slip, 2));

		parametersArray[validations] = new KTParameters(start, transition, new Gaussian(guess, sGuess), new Gaussian(slip, sSlip));
		parametersArray[validations + 1] = new KTParameters(sStart, sTransition, null, null);
	}

	/** @return The variation for the final Knowledge for the input sequences.
	 * @param aggregated - If true, will compute the variation for the aggregated Knowledge. */
	private static double computeVariation(ArrayList<Sequence> sequences, boolean aggregated)
	{
		double variation = 0;
		int count = 0;
		for (Sequence sequence : sequences)
			if (!aggregated || (sequence.finalProblem().aggregatedKnowledge.mean <= 1 && sequence.finalProblem().aggregatedKnowledge.mean >= 0))
			{
				++count;
				variation += (aggregated ? sequence.finalProblem().aggregatedKnowledge : sequence.finalProblem().knowledge).variation;
			}

		return variation / count;
	}

	private static boolean createMetrics(String inputMetrics, String inputWeight, String inputThreshold)
	{
		metrics = new ArrayList<Metric>();
		try
		{
			inputMetrics = inputMetrics.substring(1, inputMetrics.length() - 1).replaceAll(" ", "");
			if (inputMetrics.equals("")) return true;
			String[] m = inputMetrics.split(",");
			for (String metric : m)
				metrics.add(new Metric(metric));

			if (inputWeight != null)
			{
				inputWeight = inputWeight.substring(1, inputWeight.length() - 1).replaceAll(" ", "");
				if (inputWeight.equals("")) return true;
				String[] w = inputWeight.split(",");
				for (int i = 0; i < w.length - 1; ++i)
					metrics.get(i).weight = Utils.parseDouble(w[i]);
				aggregationBase = Utils.parseDouble(w[w.length - 1]);
			}

			if (inputThreshold != null)
			{
				if (!inputThreshold.startsWith("["))
				{
					double t = Double.parseDouble(inputThreshold);
					for (Metric metric : metrics)
					{
						metric.threshold = t;
						metric.thresholdReversed = metric.weight < 0;
					}
				} else
				{
					inputThreshold = inputThreshold.substring(1, inputThreshold.length() - 1).replaceAll(" ", "");
					if (inputThreshold.equals("")) return true;
					String[] t = inputThreshold.split(",");
					for (int i = 0; i < t.length; ++i)
					{
						if (t[i].startsWith("<")) metrics.get(i).thresholdReversed = true;
						if (t[i].startsWith("<") || t[i].startsWith(">")) t[i] = t[i].substring(1);
						metrics.get(i).threshold = Utils.parseDouble(t[i]);
					}
				}
			}

			return true;
		} catch (Exception e)
		{
			log("Error while reading metrics: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	/** Reads the input files and creates the corresponding Sequences.
	 * 
	 * @param set - The set to store the Sequences in.
	 * @param input - The input file with the data.
	 * @return true if it succeeded. */
	private static boolean createSequences(ArrayList<Sequence> set, File input)
	{
		set.clear();
		log("Reading input file: " + input.getName());

		int sequenceID = -1, problemID = -1, expectedKnowledge = -1, order = -1, correctness = -1, score = -1;
		HashMap<Metric, Integer> metricIndex = new HashMap<Metric, Integer>();
		try
		{
			CSVParser parser = CSVParser.parse(input, Charset.defaultCharset(), CSVFormat.DEFAULT);

			for (CSVRecord record : parser)
				if (parser.getCurrentLineNumber() == 1)
				{
					for (int i = 0; i < record.size(); ++i)
					{
						if (record.get(i).equals("sequence")) sequenceID = i;
						else if (record.get(i).equals("problem")) problemID = i;
						else if (record.get(i).equals("expected_knowledge")) expectedKnowledge = i;
						else if (record.get(i).equals("order")) order = i;
						else if (record.get(i).equals("correctness")) correctness = i;
						else if (record.get(i).equals("score")) score = i;
						for (Metric metric : metrics)
							if (record.get(i).equals(metric.name)) metricIndex.put(metric, i);
					}
					for (Metric metric : metrics)
						if (!metricIndex.containsKey(metric))
						{
							System.out.println("Metric couldn't be found in input file: " + metric.name);
							return false;
						}
				} else try
				{
					Sequence sequence = findSequence(set, record.get(sequenceID));
					Problem p = order == -1 ? new Problem(record.get(problemID)) : new Problem(record.get(problemID), Integer.parseInt(record.get(order)));
					p.expectedKnowledge = Utils.parseDouble(record.get(expectedKnowledge));
					p.isCorrect = correctness == -1 ? false : record.get(correctness).equals("1");
					p.score = score == -1 ? 0 : Utils.parseDouble(record.get(score));
					for (Metric metric : metrics)
						p.metricScores.put(metric, Utils.parseDouble(record.get(metricIndex.get(metric))));
					sequence.problems.add(p);
				} catch (NumberFormatException e)
				{
					log("Error reading problem " + record.get(1) + ": " + e.getMessage());
					e.printStackTrace();
				}
		} catch (IOException e)
		{
			log("Error reading input file: " + e.getMessage());
		}

		for (Sequence sequence : set)
			sequence.problems.sort(Comparator.naturalOrder());

		return true;
	}

	/** Exports the Sequences and calculated Knowledge to the output file.
	 * 
	 * @param output - The output file to export the Sequences to. */
	public static void exportData(File output, ArrayList<ArrayList<String>> data)
	{
		try
		{
			BufferedWriter bw = new BufferedWriter(new FileWriter(output));
			CSVPrinter printer = new CSVPrinter(bw, CSVFormat.DEFAULT);
			printer.printRecords(data);
			printer.close();
		} catch (IOException e)
		{
			log("Error creating output file: " + e.getMessage());
			// e.printStackTrace();
		}
	}

	private static void exportData(File output, String[][] data)
	{
		log("Exporting to " + output.getName() + "...");
		ArrayList<ArrayList<String>> list = new ArrayList<ArrayList<String>>();
		for (String[] array : data)
		{
			ArrayList<String> a = new ArrayList<String>();
			for (String string : array)
				a.add(string);
			list.add(a);
		}

		exportData(output, list);
	}

	/** Determines the Knowledge Sequences for each Sequence. */
	private static void findKnowledgeSequences()
	{
		for (Sequence sequence : allSequences)
			sequence.findKnowledgeSequence();
	}

	/** Determines which Problems should be used to compute Precision. */
	private static void findRepresentativeProblems()
	{
		for (Sequence sequence : allSequences)
		{
			Problem best = sequence.finalProblem();
			// Find problem with smallest distance to expected
			for (Problem p : sequence.problems)
				if (Math.abs(p.expectedKnowledge - p.knowledge.mean) < Math.abs(best.expectedKnowledge - best.knowledge.mean)) best = p;

			// Use it and all problems after
			for (int i = sequence.problems.indexOf(best); i < sequence.problems.size(); ++i)
				sequence.problems.get(i).isRepresentative = true;

		}
	}

	/** @return The Sequence matching the input ID. Creates a new one if doesn't exist. */
	private static Sequence findSequence(ArrayList<Sequence> set, String id)
	{
		for (Sequence sequence : set)
			if (sequence.name.equals(id)) return sequence;
		Sequence s = new Sequence(id);
		set.add(s);
		return s;
	}

	public static void log(String text)
	{
		System.out.println(text);
		log.add(text);
	}

	public static void main(String[] args)
	{
		mainMethod();
		File f = new File("log.txt");
		try
		{
			BufferedWriter w = new BufferedWriter(new FileWriter(f));
			for (String string : log)
				w.write(string + "\n");
			w.close();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	private static void mainMethod()
	{

		try
		{
			settings = new Properties();
			settings.load(new FileInputStream(new File("settings.properties")));
			for (String property : new String[]
			{ "aggregation_type", "aggregation_value", "correctness", "cross_validation", "expected_binary", "input_file", "metric_threshold", "metrics",
					"output_metrics", "output_params", "output_sequences", "scores", "split" })

				if (!settings.containsKey(property))
				{
					log("Missing setting: " + property);
					return;
				}
		} catch (IOException e)
		{
			log("Error reading settings file: " + e.getMessage());
			return;
		}

		if (!createMetrics(settings.getProperty("metrics"),
				settings.getProperty("aggregation_type").equals("weights") ? settings.getProperty("aggregation_value") : null,
				settings.getProperty("metric_threshold"))) return;

		File sequences = new File(settings.getProperty("input_file"));
		if (!sequences.exists())
		{
			log("Input file doesn't exist: " + settings.getProperty("input_file"));
			return;
		}

		allSequences = new ArrayList<Sequence>();
		testingSet = new ArrayList<Sequence>();
		learningSet = new ArrayList<Sequence>();
		if (!createSequences(allSequences, sequences)) return;
		if (settings.getProperty("scores").equals("compute")) computeScores();
		cleanSequences(settings.getProperty("scores").equals("compute") || settings.getProperty("scores").equals("reduce"),
				Boolean.parseBoolean(settings.getProperty("center_metrics")));

		if (!settings.getProperty("correctness").equals("true")) try
		{
			log("Applying threshold: " + settings.getProperty("correctness"));
			applyThreshold(Double.parseDouble(settings.getProperty("correctness")));
		} catch (Exception e)
		{
			log("Incorrect value for score threshold: " + settings.getProperty("correctness"));
			return;
		}

		if (!settings.getProperty("split").equals("false")) try
		{
			log("Splitting sequences...");
			ExplorationSplitter.splitSequences(Integer.parseInt(settings.getProperty("split")));
		} catch (Exception e)
		{
			log("Incorrect value for max noise: " + settings.getProperty("split"));
			return;
		}

		try
		{
			testingSize = (int) Math.ceil(allSequences.size() * Double.parseDouble(settings.getProperty("cross_validation")));
		} catch (Exception e)
		{
			log("Incorrect value for cross validation: " + settings.getProperty("cross_validation"));
			return;
		}
		validations = (int) Math.ceil(allSequences.size() * 1. / testingSize);

		applyKnowledgeTracing(null);
		for (Metric metric : metrics)
			applyKnowledgeTracing(metric);

		findRepresentativeProblems();

		if (metrics.size() != 0) aggregateMetrics();

		if (!settings.getProperty("output_params").equals("null")) exportData(new File(settings.getProperty("output_params")), outputParams());
		if (!settings.getProperty("output_sequences").equals("null")) exportData(new File(settings.getProperty("output_sequences")), outputProblems());
		if (!settings.getProperty("output_metrics").equals("null")) exportData(new File(settings.getProperty("output_metrics")), outputMetrics());
		log("Done!");
	}

	/** @return The Knowledge associated with each problem. */
	private static String[][] outputMetrics()
	{
		String[][] data = new String[totalProblems * metrics.size()][5];

		data[0][0] = "sequence";
		data[0][1] = "problem";
		data[0][2] = "metric";
		data[0][3] = "input";
		data[0][4] = "learned";

		int current = 0;
		for (Sequence sequence : allSequences)
			for (Problem problem : sequence.problems)
				for (Metric metric : metrics)
				{
					data[current][0] = sequence.name;
					data[current][1] = problem.name;
					data[current][2] = metric.name;
					data[current][3] = Double.toString(metric.initialDistribution.unreduce(problem.metricScores.get(metric)));
					data[current][4] = Double.toString(metric.initialDistribution.unreduce(problem.metricKnowledge.get(metric).mean));
					++current;
				}

		return data;
	}

	/** @return The data to output. */
	private static String[][] outputParams()
	{
		String[][] output = new String[11][2];
		output[0][0] = "param";
		output[0][1] = "value";

		output[1][0] = "P(L0)";
		output[1][1] = Double.toString(mainParameters[validations].startKnowledge);

		output[2][0] = "P(T)";
		output[2][1] = Double.toString(mainParameters[validations].transition);

		output[3][0] = "mean(P(G))";
		output[3][1] = Double.toString(mainParameters[validations].guess.mean);

		output[4][0] = "variation(P(G))";
		output[4][1] = Double.toString(mainParameters[validations].guess.variation);

		output[5][0] = "mean(P((S))";
		output[5][1] = Double.toString(mainParameters[validations].slip.mean);

		output[6][0] = "variation(P(S))";
		output[6][1] = Double.toString(mainParameters[validations].slip.variation);

		output[7][0] = "Knowledge Precision";
		output[7][1] = Double.toString(computePrecision(allSequences, false));

		output[8][0] = "Knowledge Variation";
		output[8][1] = Double.toString(computeVariation(allSequences, false));

		output[9][0] = "Aggregated Precision";
		output[9][1] = Double.toString(computePrecision(allSequences, true));

		output[10][0] = "Aggregated Variation";
		output[10][1] = Double.toString(computeVariation(allSequences, true));

		return output;
	}

	/** @return The Knowledge associated with each problem. */
	private static String[][] outputProblems()
	{
		String[][] data = new String[totalProblems][8];

		data[0][0] = "sequence";
		data[0][1] = "problem";
		data[0][2] = "computed_knowledge";
		data[0][3] = "computed_variation";
		data[0][4] = "distance_to_expected";
		data[0][5] = "aggregated_knowledge";
		data[0][6] = "aggregated_variation";
		data[0][7] = "aggregated_distance_to_expected";

		int current = 0;
		for (Sequence sequence : allSequences)
			for (Problem problem : sequence.problems)
			{
				data[current][0] = sequence.name;
				data[current][1] = problem.name;
				data[current][2] = Double.toString(problem.knowledge.mean);
				data[current][3] = Double.toString(problem.knowledge.variation);
				data[current][4] = Double.toString(Math.abs(problem.expectedKnowledge - problem.knowledge.mean));
				data[current][5] = Double.toString(problem.aggregatedKnowledge.mean);
				data[current][6] = Double.toString(problem.aggregatedKnowledge.variation);
				data[current][7] = Double.toString(Math.abs(problem.expectedKnowledge - problem.aggregatedKnowledge.mean));
				++current;
			}

		return data;
	}

	/** @return The file to be processed for SVM. */
	private static String[][] outputSVM()
	{
		int size = 0;
		for (Sequence sequence : allSequences)
			for (Problem problem : sequence.problems)
				if (problem.isRepresentative) ++size;
		String[][] data = new String[size][2 + metrics.size()];

		data[0][0] = "sequence";
		for (int i = 0; i < metrics.size(); ++i)
			data[0][i + 1] = metrics.get(i).name;
		data[0][1 + metrics.size()] = "expected";

		int current = 0;
		for (Sequence sequence : allSequences)
			for (Problem problem : sequence.problems)
				if (problem.isRepresentative)
				{
					data[current][0] = sequence.name;
					for (int i = 0; i < metrics.size(); ++i)
						data[current][i + 1] = Double.toString(metrics.get(i).initialDistribution.unreduce(problem.metricScores.get(metrics.get(i))));
					data[current][1 + metrics.size()] = Double.toString(problem.expectedKnowledge);
					++current;
				}

		return data;
	}
}
