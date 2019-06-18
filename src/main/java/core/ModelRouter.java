package core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import util.TextHandler;

public class ModelRouter {
	
	private static final String editDist = "TreeEditDistance";
	private static final String graphNav = "GraphNavigation";
	private static TextHandler th;
	
	public ModelRouter (TextHandler handler){
		
		//Disable log messages
		Logger.getRootLogger().setLevel(Level.OFF);
				
		//Initialize the text handler
		th = handler;	
	}	
	
	//Check whether there is no overlap at all between the text and the hypothesis
  	private boolean nullOverlap (String text, String hyp){

  		boolean isNull = false;

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
  			textTokens.stream().forEach((str) -> {textWords.add(str.split(";")[0]);});

  			for (String target : hypTokens){
  				String hypWord = target.split(";")[0];
  				if (textWords.contains(hypWord)){
  					overlapTokens.add(hypWord);
  				}
  			}

  			if (overlapTokens.isEmpty()){
  				isNull = true;
  			}
  		}
  		catch (Exception e){
  			e.printStackTrace();
  		}

  		return isNull;
  	}
		
	//Pre-process the text and hypothesis to decide what entailment model should be used
	public String chooseEntailmentModel (String text, String hyp){

		String model = new String();
		boolean empty = false;
		
		if (nullOverlap(text, hyp)){
			empty = true;
		}
		else{
			List<List<String>> cleanedPair = th.cleanPair(text, hyp);
			List<String> textTokens = cleanedPair.get(0);
			List<String> hypTokens = cleanedPair.get(1);
	
			if (textTokens.isEmpty() || hypTokens.isEmpty()){
				empty = true;
			}
		}	

		model = empty ? editDist : graphNav;

		return model;
	}
	
}
