package data;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONObject;

public class DataReader {
	
	//Read a dataset in text format
	@SuppressWarnings("unchecked")
	public List<JSONObject> readTextDataset (String inputfile){
		
		List<JSONObject> pairs = new ArrayList<JSONObject>();
		int lineCount = 1;
		
		try{
			BufferedReader br = new BufferedReader(new FileReader(inputfile));
			try{
				String line = new String();
				JSONObject pair = new JSONObject();
				
				while ((line = br.readLine()) != null) {
					if(!line.equals("")){
						if (lineCount == 1){
							String id = line.substring(0, line.indexOf(" "));
							String text = line.substring(line.indexOf(":")+2);
							pair.put("id", id);
							pair.put("text", text);
							lineCount = 2;
						}
						else if (lineCount == 2){
							String hyp = line.substring(line.indexOf(":")+2);
							pair.put("hypothesis", hyp);
							lineCount = 3;
						}
						else if (lineCount == 3){
							String entail = line.substring(line.indexOf(":")+2);
							pair.put("entailment", entail);
							lineCount = 1;
						}
					}
					else{
						pairs.add((JSONObject)pair.clone());
						pair.clear();
					}
				}
				pairs.add((JSONObject)pair.clone());
			} 
			catch (IOException e) {
				e.printStackTrace();
			}
		} 
		catch (FileNotFoundException f){
			f.printStackTrace();
		}
		
		return pairs;	
	}
	
}
