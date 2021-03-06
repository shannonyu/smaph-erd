/**
 *  Copyright 2014 Marco Cornolti
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package it.acubelab.smaph;

import it.unipi.di.acube.batframework.utils.Pair;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.lang.StringUtils;
import org.tartarus.snowball.ext.EnglishStemmer;

public class SmaphUtils {

	/**
	 * For each word of bold, finds the word in query that has the minimum edit
	 * distance, normalized by the word length. Returns the average of those
	 * distances.
	 * 
	 * @param query
	 *            a query.
	 * @param bold
	 *            a bold.
	 * @return the averaged normalized word-by-word edit distance of bold
	 *         against query.
	 */
	public static double getMinEditDist(String query, String bold) {
		return getMinEditDist(query, bold, null);
	}

	/**
	 * For each word of bold, finds the word in query that has the minimum edit
	 * distance, normalized by the word length. Put that word in minTokens.
	 * Returns the average of those distances.
	 * 
	 * @param query
	 *            a query.
	 * @param bold
	 *            a bold.
	 * @param minTokens
	 *            the tokens of query having minimum edit distance.
	 * @return the averaged normalized word-by-word edit distance of bold
	 *         against query.
	 */
	public static double getMinEditDist(String query, String bold,
			List<String> minTokens) {
		List<String> tokensQ = tokenize(query);
		List<String> tokensB = tokenize(bold);

		if (tokensB.size() == 0 || tokensQ.size() == 0)
			return 1;

		float avgMinDist = 0;
		for (String tokenB : tokensB) {
			float minDist = Float.MAX_VALUE;
			String bestQToken = null;
			for (String tokenQ : tokensQ) {
				float relLev = getNormEditDistance(tokenB, tokenQ);
				if (relLev < minDist) {
					minDist = relLev;
					bestQToken = tokenQ;
				}
			}
			if (minTokens != null)
				minTokens.add(bestQToken);
			avgMinDist += minDist;
		}
		return avgMinDist / tokensB.size();
	}

	/**
	 * @param tokenB
	 *            a word.
	 * @param tokenQ
	 *            another word.
	 * @return the normalized edit distance between tokenB and tokenQ.
	 */
	public static float getNormEditDistance(String tokenB, String tokenQ) {
		if (tokenQ.isEmpty() || tokenB.isEmpty())
			return 1;
		int lev = StringUtils.getLevenshteinDistance(tokenB, tokenQ);
		return (float) lev / (float) Math.max(tokenB.length(), tokenQ.length());
	}

	/**
	 * @param title
	 *            the title of a Wikipedia page.
	 * @return true iff the title is that of a regular page.
	 */
	public static boolean acceptWikipediaTitle(String title) {
		// TODO: this can definitely be done in a cleaner way.
		return !(title.startsWith("Talk:") || title.startsWith("Special:")
				|| title.startsWith("Portal:")
				|| title.startsWith("Wikipedia:")
				|| title.startsWith("Wikipedia_talk:")
				|| title.startsWith("File:") || title.startsWith("User:")
				|| title.startsWith("Category:") || title.startsWith("List") || title
					.contains("(disambiguation)"));
	}

	/**
	 * @param ftrCount
	 *            the number of features.
	 * @return a vector containing all feature ids from 1 to ftrCount.
	 */
	public static Vector<Integer> getAllFtrVect(int ftrCount) {
		Vector<Integer> res = new Vector<>();
		for (int i = 1; i < ftrCount + 1; i++)
			res.add(i);
		return res;
	}

	/**
	 * Turns a list of pairs <b,r>, where b is a bold and r is the position in
	 * which the bold occurred, to the list of bolds and the hashmap between a
	 * position and the list of bolds occurring in that position.
	 * 
	 * @param boldAndRanks
	 *            a list of pairs <b,r>, where b is a bold and r is the position
	 *            in which the bold occurred.
	 * @param positions
	 *            where to store the mapping between a position (rank) and all
	 *            bolds that appear in that position.
	 * @param bolds
	 *            where to store the bolds.
	 */
	public static void mapRankToBoldsLC(
			List<Pair<String, Integer>> boldAndRanks,
			HashMap<Integer, HashSet<String>> positions, HashSet<String> bolds) {

		for (Pair<String, Integer> boldAndRank : boldAndRanks) {
			String spot = boldAndRank.first.toLowerCase();
			int rank = boldAndRank.second;
			if (bolds != null)
				bolds.add(spot);
			if (positions != null) {
				if (!positions.containsKey(rank))
					positions.put(rank, new HashSet<String>());
				positions.get(rank).add(spot);
			}
		}

	}

	/**
	 * Turns a list of pairs <b,r>, where b is a bold and r is the position in
	 * which the bold occurred, to a mapping from a bold to the positions in
	 * which the bolds occurred.
	 * 
	 * @param boldAndRanks
	 *            a list of pairs <b,r>, where b is a bold and r is the position
	 *            in which the bold occurred.
	 * @return a mapping from a bold to the positions in which the bold
	 *         occurred.
	 */
	public static HashMap<String, HashSet<Integer>> findPositionsLC(
			List<Pair<String, Integer>> boldAndRanks) {
		HashMap<String, HashSet<Integer>> positions = new HashMap<>();
		for (Pair<String, Integer> boldAndRank : boldAndRanks) {
			String bold = boldAndRank.first.toLowerCase();
			int rank = boldAndRank.second;
			if (!positions.containsKey(bold))
				positions.put(bold, new HashSet<Integer>());
			positions.get(bold).add(rank);
		}
		return positions;
	}

	/**
	 * Given a string, replaces all words with their stemmed version.
	 * 
	 * @param str
	 *            a string.
	 * @param stemmer
	 *            the stemmer.
	 * @return str with all words stemmed.
	 */
	public static String stemString(String str, EnglishStemmer stemmer) {
		String stemmedString = "";
		String[] words = str.split("\\s+");
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			stemmer.setCurrent(word);
			stemmer.stem();
			stemmedString += stemmer.getCurrent();
			if (i != words.length)
				stemmedString += " ";
		}
		return stemmedString;
	}

	/**
	 * Compress a string with GZip.
	 * 
	 * @param str
	 *            the string.
	 * @return the compressed string.
	 * @throws IOException
	 *             if something went wrong during compression.
	 */
	public static byte[] compress(String str) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		GZIPOutputStream gzip = new GZIPOutputStream(out);
		gzip.write(str.getBytes());
		gzip.close();
		return out.toByteArray();
	}

	/**
	 * Decompress a GZipped string.
	 * 
	 * @param compressed
	 *            the sequence of bytes
	 * @return the decompressed string.
	 * @throws IOException
	 *             if something went wrong during decompression.
	 */
	public static String decompress(byte[] compressed) throws IOException {
		GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(
				compressed));
		BufferedReader bf = new BufferedReader(new InputStreamReader(gis));
		String outStr = "";
		String line;
		while ((line = bf.readLine()) != null)
			outStr += line;
		return outStr;
	}

	public static List<String> tokenize(String text) {
		text = text.replaceAll("\\W+", " ").toLowerCase();
		Vector<String> tokens = new Vector<>(Arrays.asList(text.split("\\s+")));
		tokens.remove("");
		return tokens;
	}
}
