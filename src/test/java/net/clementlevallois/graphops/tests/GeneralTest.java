/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.graphops.tests;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.StringReader;
import net.clementlevallois.graphops.GetTopNodesFromThickestEdges;
import org.junit.Test;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class GeneralTest {
    
    @Test
    public void performTest(){
        try {
            InputStream is = GeneralTest.class.getClassLoader().getResourceAsStream("test.gexf");
            GetTopNodesFromThickestEdges getTop = new GetTopNodesFromThickestEdges(is);
            String returnTopNodesAndEdges = getTop.returnTopNodesAndEdges(30);
                JsonObject jsonObject = Json.createReader(new StringReader(returnTopNodesAndEdges)).readObject();
                String nodesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("nodes"));
                String edgesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("edges"));
                System.out.println("---------- nodes ----------");
                System.out.println(nodesAsJson);
                System.out.println("");
                System.out.println("");
                System.out.println("");
                System.out.println("---------- edges----------");
                System.out.println(edgesAsJson);
            
        } catch (FileNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        }
        
    }
    
}
