package core;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

import data.DataReader;
import data.DataWriter;
import data.EntailmentDecision;
import eval.Accuracy;
import models.ContextCheck;
import models.GraphNavigation;
import models.TreeEditDistance;
import util.Configuration;
import util.IndraCall;
import util.TextHandler;

public class Entailment {
	
	private static final int maxEntries = 5;
	private static TextHandler th;
	private static String configFile;
	
	public Entailment(String kb, String conf){
		
		System.out.print("Initializing the system... ");
		
		//Disable log messages
		Logger.getRootLogger().setLevel(Level.OFF);
		
		//Get the configuration file location
		configFile = conf;
		
		//Initialize the text handler
		try{
			Configuration config = new Configuration(configFile);
			th = new TextHandler(config.params.get("wnpath"), kb);
		}
		catch (IOException e){
			e.printStackTrace();
		}
		
		System.out.println("Done.");
	}
	
	//Sort a set of values
	private <T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
		List<T> list = new ArrayList<T>(c);
		java.util.Collections.sort(list);
		return list;
	}
	
	//Annotate a dataset based on a given threshold for training
	@SuppressWarnings("unchecked")
	private List<JSONObject> getAnnotations (List<JSONObject> data, float distance){
		
		for (JSONObject pair : data){
			float pairDist = (float) pair.get("distance");
			
			if (pairDist < distance){
				pair.put("answer", "yes");
			}
			else{
				pair.put("answer", "no");
			}
		}
		
		return data;
	}
	
	//Match individual words to the multi-word phrases that contain them, if any
  	private List<String> matchPhrases (List<String> tokens, List<String> phrases){

  		List<String> matched = new ArrayList<String>();

  		for (String token : tokens){
  			String word = token.split(";")[0];
  			String pos = token.split(";")[1];
  			
  			for (int i=0; i < phrases.size(); i++){
  				String phrase = phrases.get(i).split(";")[0];

  				if (phrase.startsWith(th.normalize(word, pos)) || phrase.endsWith(th.normalize(word, pos))){
  					String entry = phrases.get(i).split(";")[0] + ";" + pos;
  					
  					if (!matched.contains(entry)){
  						matched.add(entry);
  					}	
  					break;
  				}
  			}
  		}
  		return matched;
  	}
  	
  	//Identify the pairs of words (the source, coming from the text, and the target, coming from the 
  	//hypothesis) to be sent as input to the Graph Navigation model
  	@SuppressWarnings("rawtypes")
	private List<List<String>> getSourceTargetPairs (String text, String hyp){
		
		List<List<String>> pairs = new ArrayList<List<String>>();
		List<List<String>> cleanedPair = th.cleanPair(text, hyp);
		
		try{
			List<String> textTokens = cleanedPair.get(0);
			List<String> textChunks = th.split(text);
			List<String> textPhrases = matchPhrases(textTokens, textChunks);
			
			List<String> hypTokens = cleanedPair.get(1);
			List<String> hypChunks = th.split(hyp);
			List<String> hypPhrases = matchPhrases(hypTokens, hypChunks);
			
			//Find the best pairs
			List<String> allPairs = new ArrayList<String>();
			
			//Combine all words from text to all words from hypothesis
			for (String textWord : textPhrases){
				String tWord = textWord.split(";")[0];
				String tPos = textWord.split(";")[1];
	
				for (String hypWord : hypPhrases){
					String hWord = hypWord.split(";")[0];
					String hPos = hypWord.split(";")[1];
	
					List<Map> scores = IndraCall.getResponse(tWord, Arrays.asList(hWord));
	
					for (Map score : scores){
						String item = score.get("t1") +  "#" + tPos + ";" + score.get("t2") + "#" + hPos + ";" + score.get("score");
						allPairs.add(item);
					}
				}
			}
					
			//Sort combined pairs according to their semantic similarity, using insertion sort
			String temp;
	
			for (int i=1; i < allPairs.size(); i++){			
				for (int j = i ; j > 0 ; j--){
					Double score1 = Math.abs(Double.parseDouble(allPairs.get(j).split(";")[2]));
					Double score2 = Math.abs(Double.parseDouble(allPairs.get(j-1).split(";")[2]));
	
					if (score1 > score2){ //descending order
						temp = allPairs.get(j);
						allPairs.set(j, allPairs.get(j-1));
						allPairs.set(j-1, temp);
					}
				}
			}
	
			//Get best pairs
			int maxPairs = Math.min(allPairs.size(), maxEntries);
			for (int k=0; k < maxPairs; k++){
				String item = allPairs.get(k);
				List<String> pair = Arrays.asList(item.split(";")[0], item.split(";")[1]);
				pairs.add(pair);
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
		
		return pairs;
	}
  	
  	//Write the TED threshold to the configuration file after learning it from a training dataset
  	private void writeThreshold (float threshold){
  		
  		try{
			BufferedReader br = new BufferedReader(new FileReader(configFile));
			String line = new String();
			StringBuffer inputBuffer = new StringBuffer();
			
			try{
				//Read the config file
				while ((line = br.readLine()) != null) {
		            inputBuffer.append(line);
		            inputBuffer.append('\n');
		        }
		        
				String inputStr = inputBuffer.toString();
		        inputStr = inputStr.replaceFirst("tedthreshold = .*\\n", "tedthreshold = " + threshold + "\n");
		        br.close();
		        
		        //Write the config file with the new parameter
		        try {
			        FileWriter writer = new FileWriter(configFile);
			        writer.write(inputStr);
			        writer.flush();
			        writer.close();
			        System.out.println("Threshold written to configuration file.");
			    }catch(IOException e){  
					e.printStackTrace();
				}
		        
			} catch (IOException e) {
				e.printStackTrace();
			}
	  	} 
		catch (FileNotFoundException f){
			f.printStackTrace();
		}
  	}
  	
  	//Train the TreeEditDistance component to learn the best threshold
  	@SuppressWarnings("unchecked")
	public void train (String traindataset){
		
		System.out.println("Training...");
		
		TreeEditDistance ted = new TreeEditDistance(configFile);
		DataReader dr = new DataReader();
		List<JSONObject> data = dr.readTextDataset(traindataset);
		Set<Float> distances = new HashSet<Float>();
		Double maxF1 = 0.0;
		float threshold = 0;
		
		for (JSONObject pair : data){
			String text = (String) pair.get("text");
			String hyp = (String) pair.get("hypothesis");
			float distance = ted.computeDistance(text, hyp);
			
			pair.put("distance", distance);
			
			distances.add(distance);
		}
		
		List<Float> sortedDists = asSortedList(distances);
		
		for (Float distance : sortedDists){
			List<JSONObject> annotated = getAnnotations(data, distance);
			Accuracy acc = new Accuracy();
			
			Double tp = 0.0;
			Double fp = 0.0;
			Double fn = 0.0;
			Double tn = 0.0;
			
			for (JSONObject pair : annotated){
				String entail = (String) pair.get("entailment");
				String answer = (String) pair.get("answer");
				
				if (entail.equalsIgnoreCase("yes")){
					if (answer.equalsIgnoreCase("yes")){
						tp += 1.0;
					}
					else{
						fn += 1.0;
					}
				}
				else{
					if (answer.equalsIgnoreCase("yes")){
						fp += 1.0;
					}
					else{
						tn += 1.0;
					}
				}
			}
			
			Double precision = acc.computePrecision(tp, fp);
			Double recall = acc.computeRecall(tp, fn);
			Double f1 = acc.computeF1(precision, recall);
			
			if (f1 > maxF1){
				maxF1 = f1;
				threshold = distance;
			}
		}
		
		System.out.println("Best F1: " + String.format("%.2f", maxF1) + ", obtained with the threshold " + threshold);
		
		//Write the learned threshold to the config file
		writeThreshold (threshold);
	}
	
	//Compute a single entailment
	public String processPair (String text, String hyp, String kb){

		String output = new String();
		
		//Decide which entailment model to use for the pair
		ModelRouter router = new ModelRouter(th);
		String model = router.chooseEntailmentModel(text, hyp);
		
		//Check context information
		EntailmentDecision decision = new ContextCheck(th).checkContext(text, hyp); 
		
		if (model.equals("TreeEditDistance")){			
			if (!decision.getDecision().equals("no")){ //No concluding decision, call TED
				decision = new TreeEditDistance(configFile).computeEntailment(text, hyp);
			}
			else{
				decision.setModel("TreeEditDistance");
			}
		}
		else if (model.equals("GraphNavigation")){			
			if (!decision.getDecision().equals("no")){ //No concluding decision, call GN
				List<List<String>> pairs = getSourceTargetPairs(text, hyp);
				decision = new GraphNavigation(th, kb, configFile).computeEntailment(pairs);
			}
			else{
				decision.setModel("GraphNavigation");
			}
		}
		
		output = "Using model '" + decision.getModel() + "'\n" + "Entailment: " + decision.getDecision();
		
		if (!decision.getJustification().equals("null")){
			output+= "\nJustification:\n" + decision.getJustification();
		}
		
		return output;	
	}

	//Process a whole dataset
	@SuppressWarnings("unchecked")
	public void processDataset(String inputfile, String outputfile, String kb){

		DataReader dr = new DataReader();
		List<JSONObject> data = dr.readTextDataset(inputfile);
		List<JSONObject> results = new ArrayList<JSONObject>();
		
		TreeEditDistance ted = new TreeEditDistance(configFile);
		GraphNavigation gn = new GraphNavigation(th, kb, configFile);
		ModelRouter router = new ModelRouter(th);

		//Process each pair in the dataset
		for (JSONObject item : data){
			String id = (String) item.get("id");
			String text = (String) item.get("text");
			String hyp = (String) item.get("hypothesis");

			System.out.println("Processing entailment pair #" + id);
			System.out.println("T: " + text);
			System.out.println("H: " + hyp);
			System.out.println();
			
			//Decide which entailment model to use for the pair
			String model = router.chooseEntailmentModel(text, hyp);
			
			//Check context information
			EntailmentDecision decision = new ContextCheck(th).checkContext(text, hyp); 
			
			if (model.equals("TreeEditDistance")){			
				if (!decision.getDecision().equals("no")){ //No concluding decision, call TED
					decision = ted.computeEntailment(text, hyp);
				}
				else{
					decision.setModel("TreeEditDistance");
				}
			}
			else if (model.equals("GraphNavigation")){			
				if (!decision.getDecision().equals("no")){ //No concluding decision, call GN
					List<List<String>> pairs = getSourceTargetPairs(text, hyp);
					decision = gn.computeEntailment(pairs);
				}
				else{
					decision.setModel("GraphNavigation");
				}
			}
			
			JSONObject result = (JSONObject) item.clone();
			String[] justifLines = decision.getJustification().split("\n");
			List<String> justifItems = new ArrayList<String>();

			for (String line : justifLines){
				justifItems.add(line);
			}

			result.put("model", model);
			result.put("answer", decision.getDecision());
			result.put("justification", justifItems);
			results.add((JSONObject) result.clone());
		}
		
		//Write results to file
		DataWriter dw = new DataWriter();
		dw.writeEntaimentResult(results, outputfile);

		//Compute and print accuracy
		System.out.println("\n******************************************************************\n");

		Accuracy acc = new Accuracy();
		acc.printSummary(results);
	}

}
