package data;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONObject;

public class DataWriter {
	
	//Write the results to a text file
	@SuppressWarnings("unchecked")
	public void writeEntaimentResult(List<JSONObject> results, String outputfile){
		
		List<String> records = new ArrayList<String>();
		
		System.out.println("Writing results to file " + outputfile);
		
		for (JSONObject result : results){
			String id = (String) result.get("id");
			String text = (String) result.get("text");
			String hyp = (String) result.get("hypothesis");
			String entail = (String) result.get("entailment");
			String answer = (String) result.get("answer");
			String model = (String) result.get("model");
			List<String> justification = (List<String>) result.get("justification");
			
			records.add(id + " " + "T: " + text + "\n");
			records.add(id + " " + "H: " + hyp + "\n");
			records.add(id + " " + "A: " + entail + "\n");
			records.add("Entailment: " + answer + "\n");
			records.add("Model: " + model + "\n");
			
			if (!justification.toString().equals("null")){
				records.add("Justification:\n");
				
				for (String line : justification){
					records.add(line + "\n");
				}
			}
			
			records.add("\n");
			
			try {
		        FileWriter writer = new FileWriter(outputfile);
		        for (String record: records) {
		            writer.write(record);
		        }
		        writer.flush();
		        writer.close();
		    }catch(IOException e){  
				e.printStackTrace();
			}
		}
	}

}
