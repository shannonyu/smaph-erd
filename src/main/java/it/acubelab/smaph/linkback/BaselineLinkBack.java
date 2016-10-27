package it.acubelab.smaph.linkback;

import it.unipi.di.acube.batframework.data.*;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.acubelab.smaph.SmaphUtils;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

public class BaselineLinkBack implements LinkBack {
	WikipediaApiInterface wikiApi;
	
	public BaselineLinkBack(WikipediaApiInterface api){
		this.wikiApi = api;
	}
	
	private class CompareTripleByScore implements
			Comparator<Triple<Double, String[], Tag>> {
		@Override
		public int compare(Triple<Double, String[], Tag> o1,
				Triple<Double, String[], Tag> o2) {
			double diff = o1.getLeft() - o2.getLeft();
			if (diff < 0)
				return -1;
			else if (diff == 0)
				return 0;
			else
				return 1;
		}
	}

	@Override
	public HashSet<ScoredAnnotation> linkBack(String query,
			HashMap<String[], Tag> boldsToEntities) {

		// If more than one bold points to the same entity, keep the bold with
		// smallest edit distance.
		HashMap<String, Tag> boldToEntities = new HashMap<>();
		for (String[] bolds : boldsToEntities.keySet()) {
			String bestBold = null;
			double bestDistance = Double.MAX_VALUE;
			for (String bold : bolds) {
				double minED = SmaphUtils.getMinEditDist(query, bold);
				if (minED <= bestDistance) {
					bestBold = bold;
					bestDistance = minED;
				}
			}
			String title = "";
			try {
				title = wikiApi.getTitlebyId(boldsToEntities.get(bolds).getConcept());
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (bestDistance > SmaphUtils.getMinEditDist(query, title)){
				bestDistance = SmaphUtils.getMinEditDist(query, title);
			}
			boldToEntities.put(bestBold, boldsToEntities.get(bolds));
		}

		// Compute, for each <bold, entity> pair, the list of covered query
		// keywords
		List<Triple<Double, String[], Tag>> edAndCoveredTokensAndEntity = new Vector<>();
		for (String bold : boldToEntities.keySet()) {
			List<String> coveredTokens = new Vector<>();
			double minED = SmaphUtils
					.getMinEditDist(query, bold, coveredTokens);
			edAndCoveredTokensAndEntity
					.add(new ImmutableTriple<Double, String[], Tag>(minED,
							coveredTokens.toArray(new String[] {}),
							boldToEntities.get(bold)));
		}

		// order by increasing edit distance
		Collections.sort(edAndCoveredTokensAndEntity,
				new CompareTripleByScore());

		// Take the list of query tokens; bind them to their positions in the
		// query
		List<String> tokens = SmaphUtils.tokenize(query);
		int[] tokenPositions = new int[tokens.size()];
		int lastPos = 0;
		for (int i = 0; i < tokenPositions.length; i++) {
			lastPos = query.toLowerCase().indexOf(tokens.get(i), lastPos);
			tokenPositions[i] = lastPos;
		}
		HashSet<Integer> toCover = new HashSet<>();
		for (int i = 0; i < tokens.size(); i++)
			toCover.add(i);

		// Starting from the token with minimum edit distance, select
		// annotations until all query tokens are covered or there are no
		// entities left.
		HashSet<ScoredAnnotation> result = new HashSet<>();
		int i = 0;
		while (!toCover.isEmpty() && i < edAndCoveredTokensAndEntity.size()) {
			Triple<Double, String[], Tag> t = edAndCoveredTokensAndEntity
					.get(i);
			int minPos = -1, maxPos = -1;
			for (String token : t.getMiddle()) {
				int pos = tokens.indexOf(token);
				if (!toCover.contains(pos))
					continue;
				if (pos != -1)
					if (minPos == -1 || minPos > pos)
						minPos = pos;
				if (maxPos == -1 || maxPos < pos)
					maxPos = pos;
			}
			if (minPos != -1) {
				for (int j = minPos; j <= maxPos; j++)
					toCover.remove(j);
				int start = tokenPositions[minPos];
				int end = tokenPositions[maxPos] + tokens.get(maxPos).length();
				result.add(new ScoredAnnotation(start, end - start, t
						.getRight().getConcept(), 1));
			}
			i++;
		}
		return result;
	}
}
