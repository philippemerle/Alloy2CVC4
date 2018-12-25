package edu.mit.csail.sdg.alloy4whole.solution;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "alloy")
public class Alloy
{
    @XmlElement(name = "instance")
    @JsonProperty("instances")
    public List<Instance> instances;

    public void writeToXml(String xmlFile) throws JAXBException
    {
        JAXBContext context = JAXBContext.newInstance(Alloy.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.marshal(this, new File(xmlFile));
    }

    public static Alloy readFromXml(String xmlFile) throws JAXBException
    {
        JAXBContext context = JAXBContext.newInstance(Alloy.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        Alloy alloy = (Alloy) unmarshaller.unmarshal(new File(xmlFile));
        return alloy;
    }

    public void writeToJson(String jsonFile) throws IOException
    {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(jsonFile), this);
    }

    public static Alloy readFromJson(String jsonFile) throws IOException
    {
        ObjectMapper objectMapper = new ObjectMapper();
        Alloy alloy = objectMapper.readValue(new File(jsonFile), Alloy.class);
        return alloy;
    }
}