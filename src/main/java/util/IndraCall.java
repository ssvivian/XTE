package util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class IndraCall {
	
	@SuppressWarnings("unchecked")
	//Create JSON data for querying Indra
	private static String buildJSON (String target, List<String> nodes){
		
		String data = new String();
		List<Map<String, String>> pairs = new  ArrayList<Map<String, String>>();
		
		for (String node : nodes){
			Map<String, String> pair = new HashMap<String, String>();
			pair.put("t1", target);
			pair.put("t2", node);
			pairs.add(pair);
		}
		
		JSONObject query = new JSONObject();
		query.put("corpus", "wiki-2018");
		query.put("model", "W2V");
		query.put("language", "EN");
		query.put("scoreFunction", "COSINE");
		query.put("pairs", pairs);
		
		data = query.toJSONString();
		
		return data;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	//Query Indra
	public static List<Map> getResponse (String target, List<String> nodes) {
		
		List<Map> pairs = new ArrayList<Map>();
		
		try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost request = new HttpPost("http://alphard.fim.uni-passau.de:8916/relatedness");
            String data = buildJSON(target, nodes);
            StringEntity params = new StringEntity(data, "UTF-8");
            request.addHeader("content-type", "application/json;charset=UTF-8");
            request.setEntity(params);
            HttpResponse result = httpClient.execute(request);

            String json = EntityUtils.toString(result.getEntity(), "UTF-8");
            
            try {
                JSONParser parser = new JSONParser();
                Object resultObject = parser.parse(json);

                if (resultObject instanceof JSONObject) {
                    JSONObject obj =(JSONObject)resultObject;
                    pairs = (List<Map>) obj.get("pairs");  
                }

            } 
            catch (Exception e) {
            	System.out.println("ERROR: Response is not a valid JSON object.");
            }

        } 
        catch (IOException ex) {
        	ex.printStackTrace();
        }
        return pairs;
    }
}
