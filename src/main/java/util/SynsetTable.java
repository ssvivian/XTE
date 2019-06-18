package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SynsetTable {
	
	private Map<String, List<String>> synonyms = new HashMap<String, List<String>>();
	
	public SynsetTable (){
		
		//Initialize the table of synonyms
		initializeTable("/synonyms.txt");
	}
	
	private void initializeTable (String inputfile){
		
		InputStream input = getClass().getResourceAsStream(inputfile);
		BufferedReader br = new BufferedReader(new InputStreamReader(input));
		
		try{
			String line = null;
				
			while ((line = br.readLine()) != null) {
				String pos = line.split("\\|")[0];
				String[] synList = line.split("\\|")[1].split(", ");
				
				//checking
				if (!pos.equals("noun") && !pos.equals("verb") && !pos.equals("adjective") && !pos.equals("adverb")){
					System.out.println("WARNING: invalid pos (" + pos + ")");
				}
				
				if (synList.length > 1){
					pos = pos.equals("noun") ? "NN" : (pos.equals("verb") ? "VB" : pos.equals("adjective") ? "JJ" : "RB");
					
					for (String word : synList){
						String key = word + "_" + pos;
						
						if (synonyms.containsKey(key)){
							List<String> syns = synonyms.get(key);
							
							for (String synonym : synList){
								if (!syns.contains(synonym) && ! synonym.equals(word)){
									syns.add(synonym);
								}
							}
							synonyms.put(key, syns);
						}
						else{
							List<String> syns = new ArrayList<String>();
							
							for (String synonym : synList){
								if (!synonym.equals(word)){
									syns.add(synonym);
								}
							}
							synonyms.put(key, syns);
						}	
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public List<String> getSynonyms (String word, String pos){
		
		List<String> synset = new ArrayList<String>();
		
		String key = word + "_" + pos;
		
		if (synonyms.containsKey(key)){
			synset = synonyms.get(key);
		}
		
		return synset;
	}

}
