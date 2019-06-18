package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.POS;
import edu.mit.jwi.morph.WordnetStemmer;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.LabeledWord;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;

public class TextHandler {
	
	private static final List<String> validPOS = Arrays.asList("NN", "NNS", "NNP", "NNPS", "VB", "VBD", "VBG", "VBN", "VBP", "VBZ", "VBT", "FW");
	private static final List<String> verbForm = Arrays.asList("VB", "VBD", "VBG", "VBN", "VBP", "VBZ", "VBT");
	private static final Double minNounIDF = 4.0;
	private static final Double minVerbIDF = 6.0;
	private static IDFCalc idfCalc;
	private static List<String> stopWords;	
	private IDictionary dict;
	private StanfordCoreNLP pipeline;
	
	
	public TextHandler(String wnpath, String kb) throws IOException{
		
		//Disable log messages
		Logger.getRootLogger().setLevel(Level.OFF);
		
		//Initialize the WordNet dictionary
		URL url = new URL ("file", null , wnpath);
		dict = new Dictionary(url);
		dict.open();	
		
		//Initialize the list of stop words
		stopWords = loadStopWords();
		
		//Initialize the IDF calculator
		if (kb.equals("WN")){
			idfCalc = new IDFCalc("WN");
		}
		else if (kb.equals("WKT")){		
			idfCalc = new IDFCalc("WKT");
		}
		else if (kb.equals("WKP")){		
			idfCalc = new IDFCalc("WKP");
		}
		else if (kb.equals("WBT")){		
			idfCalc = new IDFCalc("WBT");
		}
		
		//Initialize the parser
		Properties props = new Properties();
	    props.setProperty("annotators", "tokenize, ssplit, pos, lemma, parse, depparse");
	    RedwoodConfiguration.empty().capture(System.err).apply();
	    pipeline = new StanfordCoreNLP(props);
	    RedwoodConfiguration.current().clear().apply();
	}
	
	//Load the list of stop words
	private List<String> loadStopWords(){

		List<String> stopWords = new ArrayList<String>();

		InputStream input = getClass().getResourceAsStream("/stop_words.txt");
		BufferedReader br = new BufferedReader(new InputStreamReader(input));
		try{
			String line = null;

			while ((line = br.readLine()) != null) {
				stopWords.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return stopWords;
	}

	//Remove the words at the end of a sentence
	private String removeLastWords(String text, int numWords){

		String newText = text;

		if (text.contains("_")){
			for (int i=0; i < numWords; i++){
				newText = newText.contains("_") ? newText.substring(0, newText.lastIndexOf("_")) : "";
			}
		}
		else{
			newText = "";
		}

		return newText;
	}
	
	//Reverse a list of strings
	private List<String> reverse (List<String> list){

		List<String> reversed = new ArrayList<String>();

		for (int i=list.size()-1; i >= 0; i--){
			reversed.add(list.get(i));
		}

		return reversed;
	}
    
    //Returns the POS tags for every word in a piece of text
    private String tagString (String text){
    	
    	String tagged = new String();
		List<LabeledWord> words = parse(text).get(0).labeledYield();
		
		for (LabeledWord word : words){
			tagged += word.toString().replace('/', '_') + " ";
		}
		
		tagged = tagged.trim();
		
		return tagged;
    }
    
  	//Return the syntactic parse tree of a piece of text
    public List<Tree> parse (String text){
	    
		List<Tree> trees = new ArrayList<Tree>();
		
	    CoreDocument document = new CoreDocument(text);
	    pipeline.annotate(document);
	    
	    for (CoreSentence sentence : document.sentences()){
	    	Tree tree = sentence.constituencyParse();
	    	trees.add(tree.deepCopy());
	    }	
	    
	    return trees;
	}
  			
  	
	
	//Convert a list of strings to a single blank-separated string
	public String listToString (List<String> list){
		
		String str = new String();
		
		for (String token : list){
			str += token + " ";
		}
		
		return str.trim();
	}
	
	//Split a sentence into lemmatized tokens
	public List<String> tokenize (String sentence){

		List<String> words = new ArrayList<String>();

		//Replace all non-alphanumerics but dashes and single apostrophes by blanks
		sentence = sentence.replaceAll("''", "\"").replaceAll("[\\W&&[^-']]", " ").replaceAll("[\\s]+", " ").trim(); 

		CoreDocument document = new CoreDocument(sentence);
		pipeline.annotate(document);

		List<CoreLabel> tokens = document.tokens();

		for (CoreLabel token : tokens){
			String word = token.lemma().toLowerCase();
			String pos = token.tag();
			words.add(word + ";" + pos);
		}
		return words;
	}
	
	//Split a sentence into phrases, being each phrase the longest entry found in WordNet
	@SuppressWarnings({ "unused" })
	public List<String> split (String sentence) throws Exception {
				
		List<String> chunks = new ArrayList<String>();
		
		//Word stemmer
		WordnetStemmer stemmer = new WordnetStemmer(dict);
		
		//Replace all non-alphanumerics but dashes and single apostrophes by blanks
		sentence = sentence.replaceAll("''", "\"").replaceAll("[\\W&&[^-']]", " ").replaceAll("[\\s]+", " ").trim(); 
		
		//Normalize words
		String newSentence = new String();
		String[] taggedSent = tagString(sentence).split(" ");
		
		for (int i=0; i < taggedSent.length; i++){
			String token = taggedSent[i];
			String pt = token.split("_")[1];
			String word = normalize(token.split("_")[0], pt);
			
			newSentence += word + " "; 
			
		}
		
		sentence = newSentence.trim();
		
		String entry = sentence.replaceAll(" ", "_");
		String currentEntry = entry;
		IWord word;
		String chunk;
			
		//Scans the sentence from left to right. Initially, the whole sentence is considered an entry;
		//if it is not found in WN, the leftmost word is recursively removed until a valid entry is identified
		while (entry.length() > 0){
			while(entry.length() >= 1){
				boolean skip = false;
				boolean isVerbForm = false;
				POS pos = POS.NOUN;
				String newEntry = entry;
					
				List<String> wordStems = stemmer.findStems(entry, pos);
				String pt = new String();
					
				//Get the word/phrase stem
				if (wordStems.size() > 0){
					newEntry = wordStems.get(0);
				}
					
				if (!entry.contains("_")){ //a single word
					//Get the POS tag
					String tagged = tagString(entry);
					pt = tagged.substring(tagged.indexOf('_')+1, tagged.contains(" ") ? tagged.indexOf(" ") : tagged.length()).trim();
						
					if (! validPOS.contains(pt)){ //not a noun, verb, adjective or adverb
						chunk = entry + ";" + pt;
						chunks.add(chunk);
						entry = removeLastWords(currentEntry, 1);
						currentEntry = entry;
						skip = true;
						break;
					}
					else{
						if (verbForm.contains(pt)){ //ensure that words that are both a noun and a verb will be correctly located if the POS tagger has already classified them as verbs
							pos = POS.VERB;
							wordStems = stemmer.findStems(entry, pos);
							
							//Get the verb stem
							if (wordStems.size() > 0){
								newEntry = wordStems.get(0);
							}
								
							isVerbForm = true;
						}
					}
				}
					
				if (!skip){
					if (isVerbForm){ //single-word verbs
						IIndexWord words = dict.getIndexWord(newEntry, pos);
						try{
							word = dict.getWord(words.getWordIDs().get(0));
							chunk = entry.replaceAll("_", " ") + ";" + pt;
							chunks.add(chunk);
							entry = removeLastWords(currentEntry, entry.contains("_") ? entry.split("_").length : 1);
							currentEntry = entry;
							break;
						}
						catch (NullPointerException npen){ //verb not in WordNet
							chunk = entry + ";" + pt;
							chunks.add(chunk);
							entry = removeLastWords(currentEntry, 1);
							currentEntry = entry;
							break;
						}
					}	
					else{ //single-word nouns, adjectives and adverbs, and all multiple-words expressions				
						IIndexWord nouns = dict.getIndexWord(newEntry, POS.NOUN);
						try{
							word = dict.getWord(nouns.getWordIDs().get(0));
							chunk = entry.replaceAll("_", " ") + ";NN";
							chunks.add(chunk);
							entry = removeLastWords(currentEntry, entry.contains("_") ? entry.split("_").length : 1);
							currentEntry = entry;
							break;
						}
						catch (NullPointerException npen){
							IIndexWord verbs = dict.getIndexWord(newEntry, POS.VERB);
							try{
								word = dict.getWord(verbs.getWordIDs().get(0));
								chunk = entry.replaceAll("_", " ") + ";VB";
								chunks.add(chunk);
								entry = removeLastWords(currentEntry, entry.contains("_") ? entry.split("_").length : 1);
								currentEntry = entry;
								break;
							}
							catch (NullPointerException npev){
								IIndexWord adjs = dict.getIndexWord(newEntry, POS.ADJECTIVE);
								try{
									word = dict.getWord(adjs.getWordIDs().get(0));
									chunk = entry.replaceAll("_", " ") + ";JJ";
									chunks.add(chunk);
									entry = removeLastWords(currentEntry, entry.contains("_") ? entry.split("_").length : 1);
									currentEntry = entry;
									break;
								}
								catch (NullPointerException npea){
									IIndexWord advs = dict.getIndexWord(newEntry, POS.ADVERB);
									try{
										word = dict.getWord(advs.getWordIDs().get(0));
										chunk = entry.replaceAll("_", " ") + ";RB";
										chunks.add(chunk);
										entry = removeLastWords(currentEntry, entry.contains("_") ? entry.split("_").length : 1);
										currentEntry = entry;
										break;
									}
									catch (NullPointerException nper){ // word not found in any grammatical class
										if (entry.contains("_")){
											entry = entry.substring(entry.indexOf("_")+1, entry.length());
										}
										else{
											chunk = entry.replaceAll("_", " ") + ";" + pt;
											chunks.add(chunk);
											entry = removeLastWords(currentEntry, 1);
											currentEntry = entry;
											break;
										}
									}
								}
							}
						}
					}	
				}	
			}
		}
		return reverse(chunks);
	}
	
	//Find the basic form of a word
	public String normalize (String word, String posTag){
		
		String stem = word;
		
		WordnetStemmer stemmer = new WordnetStemmer(dict);
		
		if (!word.equals("'s")){
			word = word.replaceAll("'s", "").replaceAll(" ", "_");
			POS pos = null;
			
			if (posTag.startsWith("NN")){
				pos = POS.NOUN;
			}
			else if (posTag.startsWith("VB")){
				pos = POS.VERB;
			}
			else if (posTag.startsWith("JJ")){
				pos = POS.ADJECTIVE;
			}
			else if (posTag.startsWith("RB")){
				pos = POS.ADVERB;
			}
			
			if (pos != null){
				try{
					List<String> wordStems = stemmer.findStems(word, pos);
					
					if (wordStems.size() > 0){
						stem = wordStems.get(0);
					}
				}
				catch (IllegalArgumentException e){
					stem = "";
				}
			}
		}	
		return stem;
	}
	
	//Remove words with low IDF from a list of words
	public List<String> removeLowIDF (List<String> wordList){

		List<String> newList = wordList;
		List<String> lowIDF = new ArrayList<String>();

		for (String source : wordList){
			String word = source.split(";")[0];
			String pos = source.split(";")[1];

			if (pos.startsWith("N") && idfCalc.getIDF(word) < minNounIDF){
				lowIDF.add(source);
			}
			else if (pos.startsWith("V") && idfCalc.getIDF(word) < minVerbIDF){
				lowIDF.add(source);
			}
		}

		Predicate<String> hasLowIDF = s -> lowIDF.contains(s);
		newList.removeIf(hasLowIDF);

		return newList;	
	}
	
	//Remove from the text-hypothesis pair all the information that is irrelevant for the entailment decision
  	public List<List<String>> cleanPair (String text, String hyp){
  		
  		List<List<String>> cleaned = new ArrayList<List<String>>();
  		
  		try {	
  			List<String> textTokens = tokenize(text);
  			List<String> hypTokens = tokenize(hyp);
  			List<String> overlap = new ArrayList<String>();
  					
  			List<String> textWords = new ArrayList<String>();
  			textTokens.stream().forEach((str) -> {textWords.add(str.split(";")[0]);});
  			
  			for (String target : hypTokens){
  				String hypWord = target.split(";")[0];
  				if (textWords.contains(hypWord)){
  					overlap.add(hypWord);
  				}
  			}
  			
  			//Remove overlapping words from the hypothesis		
  			Predicate<String> redundant = s -> overlap.contains(s.split(";")[0]);
  			hypTokens.removeIf(redundant);
  			
  			//Remove stop words from the hypothesis
  			Predicate<String> isStopWord = s -> isStopWord(s.split(";")[0]);
  			hypTokens.removeIf(isStopWord);
  			
  			//If there are remaining words in the hypothesis, check the text
  			if (!hypTokens.isEmpty()){
  				//Remove overlapping words from the text
  				textTokens.removeIf(redundant);

  				//Remove stop words from the text
  				textTokens.removeIf(isStopWord);
  			}
  			
  			cleaned.add(textTokens);
  			cleaned.add(hypTokens);
  			
  		}
  		catch (Exception e){
  			e.printStackTrace();
  		}
  		return cleaned;
  	}
	
	//Capitalize a word
	public String capitalize(String word) {
	    
		if (word == null || word.length() == 0) {
	        return word;
	    }
	    return word.substring(0, 1).toUpperCase() + word.substring(1);
	}
	
	//Check whether a character is a vowel
	public boolean isVowel (char c){
		
		if (c == 'A' || c == 'a' || c == 'E' || c == 'e' || c == 'I' || c == 'i' ||
				c == 'O' || c == 'o' || c == 'U' || c == 'u'){
			return true;
		}
		else{
			return false;
		}
	}
	
	//Check whether a word is a stop word
	public boolean isStopWord (String word){
		
		return stopWords.contains(word.toLowerCase());
	}
	
}
