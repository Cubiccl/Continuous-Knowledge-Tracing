package ckt;

import java.io.*;
import java.text.DecimalFormat;

import ckt.KTParameters.Gaussian;

public class Utils
{

	/** Displays a double with max size of 6. */
	public static String display(double d)
	{
		String s = Double.toString(d);
		if (s.length() > 6) return s.substring(0, 7);
		while (s.length() <= 6)
			s += " ";
		return s;
	}

	/** Estimates a Gaussian distribution from the input experimental values. */
	public static Gaussian makeGaussian(double[] values)
	{
		double mean = 0;
		int size = 0;
		for (int i = 0; i < values.length; ++i)
			if (!Double.isNaN(values[i]))
			{
				mean += values[i];
				++size;
			}
		mean /= size;

		double variation = 0;
		for (int i = 0; i < values.length; ++i)
			if (!Double.isNaN(values[i]))
			{
				variation += Math.pow(values[i] - mean, 2);
				++size;
			}
		variation = Math.sqrt(variation / size);

		return new Gaussian(mean, variation);
	}

	/** Estimates a Gaussian distribution from the input experimental values. */
	public static Gaussian makeGaussian(Double[] values)
	{
		double[] convert = new double[values.length];
		for (int i = 0; i < convert.length; ++i)
			convert[i] = values[i];
		return makeGaussian(convert);
	}

	/** Parses a Double. */
	public static Double parseDouble(String input)
	{
		try
		{
			return Double.parseDouble(input);
		} catch (Exception e)
		{
			return Double.parseDouble(input.replaceAll(",", "."));
		}
	}

	public static String readTextFile(String path)
	{
		File f = new File(path);
		if (!f.exists()) return null;

		String data = "", line;
		try
		{
			BufferedReader br = new BufferedReader(new FileReader(f));
			while ((line = br.readLine()) != null)
				data += line + "\n";
			br.close();
			return data;
		} catch (IOException e)
		{
			e.printStackTrace();
			return null;
		}
	}

	public static String toString(double d)
	{
		return (new DecimalFormat("#.##########").format(d)).replaceAll(",", ".");
	}

}
