package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HypernymTable {
	
	private Map<String, List<String>> hypernyms = new HashMap<String, List<String>>();
	
	public HypernymTable (){
		
		//Initialize the table of hypernyms
		initializeTable("/hypernyms.txt");
	}
	
	private void initializeTable (String inputfile){
		
		InputStream input = getClass().getResourceAsStream(inputfile);
		BufferedReader br = new BufferedReader(new InputStreamReader(input));
		
		try{
			String line = null;
				
			while ((line = br.readLine()) != null) {
				String pos = line.split("\\|")[0];
				String[] synset = line.split("\\|")[1].split(", ");
				String[] hypList = line.split("\\|")[2].split(", ");
				
				pos = pos.equals("noun") ? "NN" : "VB";
				
				for (String word : synset){
					String key = word + "_" + pos;
						
					if (hypernyms.containsKey(key)){
						List<String> hyps = hypernyms.get(key);
							
						for (String hypernym : hypList){
							if (!hyps.contains(hypernym)){
								hyps.add(hypernym);
							}	
						}
						hypernyms.put(key, hyps);
					}
					else{
						List<String> hyps = new ArrayList<String>();
							
						for (String hypernym : hypList){
							hyps.add(hypernym);
						}
						hypernyms.put(key, hyps);	
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public List<String> getHypernyms (String word, String pos){
		
		List<String> hypernymSet = new ArrayList<String>();
		
		String key = word + "_" + pos;
		
		if (hypernyms.containsKey(key)){
			hypernymSet = hypernyms.get(key);
		}
		
		return hypernymSet;
	}

}
