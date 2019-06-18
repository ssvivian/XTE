package models;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import data.EntailmentDecision;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import util.AntonymTable;
import util.HypernymTable;
import util.SynsetTable;
import util.TextHandler;

public class ContextCheck {
	
	private static SynsetTable synonyms = new SynsetTable();
	private static AntonymTable antonyms = new AntonymTable();
	private static HypernymTable hypernyms = new HypernymTable();
	private static TextHandler th;
	
	public ContextCheck (TextHandler handler){
		
		//Initialize the text handler
		th = handler;
	}
	
	//Check whether two words are synonyms
	private boolean areSynonyms (String word1, String word2){

		boolean syns = false;

		//For hypernym detection purposes, both noun and verb forms of a word are considered
		List<String> wordSyns = synonyms.getSynonyms(word1, "NN");
		wordSyns.addAll(synonyms.getSynonyms(word1, "VB"));
		
		if (wordSyns.contains(word2)){
			syns = true;
		}

		return syns;
	}
	
	//Check whether there is a total overlap between the text and the hypothesis
  	private boolean totalOverlap (String text, String hyp){

  		boolean empty = false;

  		try {	
  			List<String> textTokens = th.tokenize(text);
  			List<String> hypTokens = th.tokenize(hyp);
  			List<String> overlapTokens = new ArrayList<String>();

  			//Remove stop words from the text and hypothesis
  			Predicate<String> isStopWord = s -> th.isStopWord(s.split(";")[0]);
  			textTokens.removeIf(isStopWord);
  			hypTokens.removeIf(isStopWord);

  			//Compute overlap
  			List<String> textWords = new ArrayList<String>();
  			List<String> hypWords = new ArrayList<String>();
  			textTokens.stream().forEach((str) -> {textWords.add(str.split(";")[0]);});
  			hypTokens.stream().forEach((str) -> {hypWords.add(str.split(";")[0]);});

  			for (String textWord : textWords){
  				if (hypWords.contains(textWord)){
  					overlapTokens.add(textWord);
  				}
  			}

  			if (overlapTokens.equals(textWords)){
  				empty = true;
  			}
  		}
  		catch (Exception e){
  			e.printStackTrace();
  		}
  		
  		return empty;
  	}
	
	//Returns all the subtrees of a parse tree that match a given TRegex expression
  	private List<Tree> getAllTreeMatches (String expression, Tree parseTree){

  		List<Tree> matches = new ArrayList<Tree>();

  		TregexPattern pattern = TregexPattern.compile(expression);
  		TregexMatcher matcher = pattern.matcher(parseTree);

  		while (matcher.findNextMatchingNode()){
  			matches.add(matcher.getMatch().deepCopy());
  		}
  		return matches;
  	}
	
	//Check whether the hypothesis has more clauses than the amount that can be satisfied by the text
  	private boolean clauseOverflow (String text, String hyp){
  		
  		boolean overflow = false;
  		
  		Tree textTree = th.parse(text).get(0);
  		Tree hypTree = th.parse(hyp).get(0);
  		
  		String expression = "@CC $+ S | @CC $+ @VP | @SBAR";
  		
  		//Get the number of clauses in the text and the hypothesis
  		int totalClausesText = getAllTreeMatches(expression, textTree).size();
  		int totalClausesHyp = getAllTreeMatches(expression, hypTree).size();
  		
  		if (totalClausesHyp > totalClausesText && !totalOverlap(text, hyp)){
  			overflow = true;
  		}
  		
  		return overflow;
  	}
  	
  	//Check whether the hypothesis is a simple negation of the text or vice-versa
  	private boolean isNegation (String text, String hyp){
  		
  		boolean negation = false;
  			
  		List<String> textTokens = th.tokenize(text);
  		List<String> hypTokens = th.tokenize(hyp);
  		List<String> overlapTokens = new ArrayList<String>();
  		
  		//Compute overlap
  		List<String> textWords = new ArrayList<String>();
  		List<String> hypWords = new ArrayList<String>();
  		textTokens.stream().forEach((str) -> {textWords.add(str.split(";")[0]);});
  		hypTokens.stream().forEach((str) -> {hypWords.add(str.split(";")[0]);});

  		for (String hypWord : hypWords){
  			if (textWords.contains(hypWord)){
  				overlapTokens.add(hypWord);
  			}
  		}
  		
  		//Remove overlapping words from the text and the hypothesis		
  		Predicate<String> redundant = s -> overlapTokens.contains(s.split(";")[0]);
  		textWords.removeIf(redundant);
  		hypWords.removeIf(redundant);
  		
  		//Only word in the text not present in the hypothesis is a negation, regardless of what remains in the hypothesis
  		if ((textWords.size() == 1 && th.listToString(textWords).equalsIgnoreCase("not")) ||
  				(textWords.size() == 2 && (th.listToString(textWords).equalsIgnoreCase("there no") || th.listToString(textWords).equalsIgnoreCase("there not"))) ||
  				(textWords.size() == 2 && (th.listToString(textWords).equalsIgnoreCase("do not"))) ||
  				(textWords.size() == 3 && (th.listToString(textWords).equalsIgnoreCase("there be no") || th.listToString(textWords).equalsIgnoreCase("there be not")))){
  			negation = true;
  		}
  		//No words remain in the text, and the only word remaining in the hypothesis is a negation
  		else if (textWords.size() == 0 && ((hypWords.size() == 1 && th.listToString(hypWords).equalsIgnoreCase("not")) ||
  					(hypWords.size() == 2 && (th.listToString(hypWords).equalsIgnoreCase("there no") || th.listToString(hypWords).equalsIgnoreCase("there not"))) ||
  					(hypWords.size() == 2 && (th.listToString(hypWords).equalsIgnoreCase("do not"))) ||
  					(hypWords.size() == 3 && (th.listToString(hypWords).equalsIgnoreCase("there be no") || th.listToString(hypWords).equalsIgnoreCase("there be not"))))){
  			negation = true;
  		}
  		
  		return negation;
  	}
  	
  	//Check whether the text and the hypothesis contains antonyms
  	private boolean isOpposition (String text, String hyp){
  		
  		boolean opposition = false;
  		
  		List<String> textTokens = th.tokenize(text);
  		List<String> hypTokens = th.tokenize(hyp);
  		List<String> overlapTokens = new ArrayList<String>();
  		
  		//Compute overlap
  		List<String> textWords = new ArrayList<String>();
  		List<String> hypWords = new ArrayList<String>();
  		textTokens.stream().forEach((str) -> {textWords.add(str.split(";")[0]);});
  		hypTokens.stream().forEach((str) -> {hypWords.add(str.split(";")[0]);});

  		for (String hypWord : hypWords){
  			if (textWords.contains(hypWord)){
  				overlapTokens.add(hypWord);
  			}
  		}
  		
  		//Remove overlapping words from the text and the hypothesis		
  		Predicate<String> redundant = s -> overlapTokens.contains(s.split(";")[0]);
  		textWords.removeIf(redundant);
  		hypWords.removeIf(redundant);
  		
  		//Search the hypothesis as a string (and not as a list) to get multi-word expressions
  		String normalizedHyp = th.listToString(hypWords);
  		
  		for (String textWord : textWords){
  			List<String> wordAntonyms = antonyms.getAntonyms(textWord.replaceAll(" ", "_"));
  			
  			for (String antonym : wordAntonyms){
  				if (normalizedHyp.contains(antonym.replaceAll("_", " "))){
  					opposition = true;
  					break;
  				}
  			}
  		}
  		
  		return opposition;
  	}
  	
  	//Checks whether the hypothesis is erroneously specializing a concept from the text
  	private boolean hasInverseSpecialization (String text, String hyp){
  		
  		boolean inverseSpec = false;
  		
  		if (!totalOverlap(text, hyp)){
	  		try{
		  		List<String> textTokens = th.split(text);
		  		List<String> hypTokens = th.split(hyp);
		  		List<String> overlapTokens = new ArrayList<String>();
		  		
		  		//Compute overlap	  		
		  		for (String textToken : textTokens){
		  			if (hypTokens.contains(textToken)){
		  				overlapTokens.add(textToken);
		  			}
		  		}
		  		
		  		//Remove overlapping words from the text and the hypothesis		 		
		  		Predicate<String> redundant = s -> overlapTokens.contains(s);
		  		textTokens.removeIf(redundant);
		  		hypTokens.removeIf(redundant);
		  		
		  		List<String> textWords = new ArrayList<String>();
		  		textTokens.stream().forEach((str) -> {textWords.add(str.split(";")[0]);});
		  		
		  		for (String hypToken : hypTokens){
		  			String hypWord = hypToken.split(";")[0];
		  			String hypPos = hypToken.split(";")[1];
		  			List<String> wordHypernyms = hypernyms.getHypernyms(hypWord.replaceAll(" ", "_"), hypPos);
		  			
		  			for (String hypernym : wordHypernyms){
		  				if (textWords.contains(hypernym) && !areSynonyms(hypWord, hypernym) && !hypernym.equals("be")){ 
		  					inverseSpec = true;
		  					break;
		  				}
		  			}
		  		}
		  	
	  		}
	  		catch (Exception e){
	  			e.printStackTrace();
	  		}
  		}
  		
  		return inverseSpec;
  	}
	
	//Look for a simple negation, a simple opposition, clause unsatisfiability or inverse specialization
  	//in the entailment pair
	public EntailmentDecision checkContext (String text, String hyp){
		
		EntailmentDecision decision = new EntailmentDecision();
		
		if (isNegation(text, hyp) || isOpposition(text, hyp) || 
				clauseOverflow(text, hyp) || hasInverseSpecialization(text, hyp)){
			decision.setDecision("no");
			decision.setJustification("null");
		}
		else{
			decision.setDecision("null");
		}
		
		return decision;
	}

}
