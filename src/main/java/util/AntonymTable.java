package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AntonymTable {
	
	private Map<String, List<String>> antonyms = new HashMap<String, List<String>>();
	
	public AntonymTable (){
		
		//Initialize the table of synonyms
		initializeTable("/antonyms.txt");
	}
	
	private void initializeTable (String inputfile){
		
		InputStream input = getClass().getResourceAsStream(inputfile);
		BufferedReader br = new BufferedReader(new InputStreamReader(input));
		
		try{
			String line = null;
				
			while ((line = br.readLine()) != null) {
				String key = line.split("\\|")[0];
				String[] antList = line.split("\\|")[1].split(", ");
				
				if (antList.length > 0){	
					if (antonyms.containsKey(key)){
						List<String> ants = antonyms.get(key);

						for (String antonym : antList){
							if (!ants.contains(antonym)){
								ants.add(antonym);
							}
							
							//add also an entry in the reverse direction
							if (!antonyms.containsKey(antonym)){
								List<String> reverseList = new ArrayList<String>();
								reverseList.add(key);
								antonyms.put(antonym, reverseList);
							}
							else{
								List<String> reverseList = antonyms.get(antonym);
								
								if (!reverseList.contains(key)){
									reverseList.add(key);
									antonyms.put(antonym, reverseList);
								}	
							}
						}
						antonyms.put(key, ants);
					}
					else{
						List<String> ants = new ArrayList<String>();

						for (String antonym : antList){
							ants.add(antonym);
							
							//add also an entry in the reverse direction
							if (!antonyms.containsKey(antonym)){
								List<String> reverseList = new ArrayList<String>();
								reverseList.add(key);
								antonyms.put(antonym, reverseList);
							}
							else{
								List<String> reverseList = antonyms.get(antonym);
								
								if (!reverseList.contains(key)){
									reverseList.add(key);
									antonyms.put(antonym, reverseList);
								}
							}
						}
						antonyms.put(key, ants);
					}	
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public List<String> getAntonyms (String word){
		
		List<String> antonymList = new ArrayList<String>();
		
		if (antonyms.containsKey(word)){
			antonymList = antonyms.get(word);
		}
		
		return antonymList;
	}

}
