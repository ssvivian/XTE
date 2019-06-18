package util;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Configuration {
	
	public Map<String, String> params = new HashMap<String, String>();
	
	//Read the parameters in the configuration file
	public Configuration (String configfile){
		
		try{
			BufferedReader br = new BufferedReader(new FileReader(configfile));
		
			try{
				String line = null;
				
				while ((line = br.readLine()) != null) {
					
					if (!line.equals("") && !line.startsWith("#")){
						String key = line.split(" = ")[0];
						String value = line.split(" = ")[1].replaceAll("\\\\", "\\\\\\\\"); //Replacing a single backlash by two ones!
						
						params.put(key, value);
					}
				}
			
			} catch (IOException e) {
				e.printStackTrace();
			}
		} 
		catch (FileNotFoundException f){
			f.printStackTrace();
		}	
	}

}
