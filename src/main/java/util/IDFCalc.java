package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IDFCalc {
	
	private static final String WNCorpus = "/WN_gloss_corpus.txt";
	private static final String WKTCorpus = "/WKT_gloss_corpus.txt";
	private static final String WKPCorpus = "/WKP_gloss_corpus.txt";
	private static final String WBTCorpus = "/WBT_gloss_corpus.txt";
	private static final Double maxIDF = 15.0;
	private Map<String, Double> idfs = new HashMap<String, Double>();
	
	public IDFCalc (String kb){
		
		String file = kb.equals("WN") ? WNCorpus : (kb.equals("WKT") ? WKTCorpus : (kb.equals("WKP") ? WKPCorpus : WBTCorpus));
		List<List<String>> corpus = buildCorpus(file);
		
		initializeIDFs(corpus);
	}
	
	//Read raw data
	private List<String> loadData(String inputfile){
		
		List<String> docs = new ArrayList<String>();
		
		InputStream input = getClass().getResourceAsStream(inputfile);
		BufferedReader br = new BufferedReader(new InputStreamReader(input));
		try{
			String line = null;
				
			while ((line = br.readLine()) != null) {
				docs.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return docs;
	}
	
	//Format each document as a list of words
	private List<List<String>> buildCorpus(String inputfile){
		
		List<List<String>> newCorpus = new ArrayList<List<String>>();
		List<String> docs = loadData(inputfile);
		
		for (String doc : docs){
			List<String> newDoc = new ArrayList<String>();
			String[] words = doc.replaceAll("''", "\"").replaceAll("[\\W&&[^-']]", " ").replaceAll("[\\s]+", " ").trim().split(" ");
			
			for (String word : words){
				newDoc.add(word);
			}
			
			newCorpus.add(newDoc);
		}
		
		return newCorpus;
	}

	//Compute the IDF for all the words in the corpus
	private void initializeIDFs(List<List<String>> corpus){
		
		Map<String, Integer> freqs = new HashMap<String, Integer>();
		
		//Get frequencies
		for (List<String> doc : corpus){
			for (String word : doc){
				if (freqs.containsKey(word.toLowerCase())){
					int freq = freqs.get(word.toLowerCase()) + 1;
					freqs.put(word.toLowerCase(), freq);
				}
				else{
					freqs.put(word.toLowerCase(), 1);
				}
			}
		}
		
		//Compute IDF
		for (String word : freqs.keySet()){
			int freq = freqs.get(word);
			Double idf = Math.log(corpus.size() / freq);
			idfs.put(word, idf);
		}	
	}
	
	//Return the IDF of a word or a value higher than the highest IDF in case the word is not in the corpus
	public Double getIDF(String word){
		
		if(idfs.containsKey(word.toLowerCase())){
			return idfs.get(word.toLowerCase());
		}
		else{
			return maxIDF;
		}		
	}

}
