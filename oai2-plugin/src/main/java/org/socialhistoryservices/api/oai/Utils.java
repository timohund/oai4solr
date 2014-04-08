/*
 * OAI4Solr exposes your Solr indexes by adding a OAI2 protocol handler.
 *
 *     Copyright (c) 2011-2014  International Institute of Social History
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.socialhistoryservices.api.oai;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.response.SolrQueryResponse;
import org.openarchives.oai2.*;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class
 */
public class Utils {

    final private static Pattern datestampPattern = Pattern.compile("^([\\+-]?\\d{4}(?!\\d{2}\\b))((-?)((0[1-9]|1[0-2])(\\3([12]\\d|0[1-9]|3[01]))?|W([0-4]\\d|5[0-2])(-?[1-7])?|(00[1-9]|0[1-9]\\d|[12]\\d{2}|3([0-5]\\d|6[1-6])))([T\\s]((([01]\\d|2[0-3])((:?)[0-5]\\d)?|24\\:?00)([\\.,]\\d+(?!:))?)?(\\17[0-5]\\d([\\.,]\\d+)?)?([zZ]|([\\+-])([01]\\d|2[0-3]):?([0-5]\\d)?)?)?)?$");

    final private static HashMap<String, Object> parameters = new HashMap<String, Object>();

    public static boolean error(SolrQueryResponse response, OAIPMHerrorcodeType code) {

        OAIPMHtype oai = (OAIPMHtype) response.getValues().get("oai");
        OAIPMHerrorType error = new OAIPMHerrorType();
        error.setCode(code);
        oai.getError().add(error);
        return false;
    }

    public static boolean error(SolrQueryResponse response, String value, OAIPMHerrorcodeType code) {

        OAIPMHtype oai = (OAIPMHtype) response.getValues().get("oai");
        OAIPMHerrorType error = new OAIPMHerrorType();
        error.setValue(value);
        error.setCode(code);
        oai.getError().add(error);
        return false;
    }

    public static RecordType error(Exception e) {

        RecordType rt = new RecordType();
        MetadataType md = new MetadataType();
        md.setAny(e.getMessage());
        rt.setMetadata(md);
        return rt;
    }

    /**
     * isValidIdentifier
     * <p/>
     * See if the identifier exists or is invalid.
     */
    public static boolean isValidIdentifier(SolrQueryResponse response, RequestType oaiRequest) {

        final String identifier = oaiRequest.getIdentifier();
        if (identifier == null)
            return error(response, OAIPMHerrorcodeType.ID_DOES_NOT_EXIST);

        String[] split = identifier.split(":", 3);
        return (split.length == 3 && split[0].equals("oai") && split[2].length() != 0) || error(response, OAIPMHerrorcodeType.ID_DOES_NOT_EXIST);
    }

    /**
     * isAvailableIdentifier
     * <p/>
     * Use to check if a GetRecord verb produced a single record.
     *
     * @param recordCount Number of records
     * @return true if we have the expected number
     */
    public static boolean isAvailableIdentifier(SolrQueryResponse response, int recordCount) {

        return recordCount == 1 || error(response, OAIPMHerrorcodeType.ID_DOES_NOT_EXIST);
    }

    /**
     * hasMatchingRecords
     * <p/>
     * Use to check if the resultset has records.
     *
     * @param recordCount Number of records
     * @return true if we have more than zero records
     */
    public static boolean hasMatchingRecords(SolrQueryResponse response, int recordCount) {

        return recordCount != 0 || error(response, OAIPMHerrorcodeType.NO_RECORDS_MATCH);
    }

    /**
     * isValidMetadataPrefix
     * <p/>
     * See if the requested metadataPrefix is supported.
     *
     * @return True if supported
     */
    public static boolean isValidMetadataPrefix(SolrQueryResponse response, RequestType oaiRequest) {

        final String metadataPrefix = oaiRequest.getMetadataPrefix();
        if (metadataPrefix == null) {
            return error(response, OAIPMHerrorcodeType.NO_METADATA_FORMATS);
        }
        return parameters.containsKey(metadataPrefix) || error(response, OAIPMHerrorcodeType.CANNOT_DISSEMINATE_FORMAT);
    }

    /**
     * isValidSet
     * <p/>
     * See if we support ListSets. If so, check if the setSpec exists.
     *
     * @param setSpec The requested set
     * @return True if we have a declared setSpec matching the request
     */
    public static boolean isValidSet(String setSpec, SolrQueryResponse response) {

        if (setSpec == null || setSpec.isEmpty())
            return true;
        final Object o = getParam("ListSets");
        if (o == null) {
            return error(response, OAIPMHerrorcodeType.NO_SET_HIERARCHY);
        }
        OAIPMHtype oaipmHtype = (OAIPMHtype) o;
        final List<SetType> sets = oaipmHtype.getListSets().getSet();
        for (SetType setType : sets) {
            if (setType.getSetSpec().equals(setSpec))
                return true;
        }
        return error(response, String.format("Set argument doesn't match any sets. The setSpec was '%s'", setSpec), OAIPMHerrorcodeType.NO_RECORDS_MATCH);
    }

    /**
     * getGregorianDate
     * <p/>
     * Convert a Java data into a GregorianCalendar date.
     */
    public static XMLGregorianCalendar getGregorianDate(Date date) {

        GregorianCalendar c = new GregorianCalendar();
        c.setTime(date);
        XMLGregorianCalendar date2 = null;
        try {
            date2 = DatatypeFactory.newInstance().newXMLGregorianCalendar(c);
        } catch (DatatypeConfigurationException e) {
            e.printStackTrace();
        }
        return date2;
    }

    public static String join(String[] s, String glue) {

        int k = s.length;
        if (k == 0) return null;
        StringBuilder out = new StringBuilder();
        out.append(s[0]);
        for (int x = 1; x < k; ++x)
            out.append(glue).append(s[x]);
        return out.toString();
    }

    public static Object getParam(String key) {

        return parameters.get(key);
    }

    public static Object getParam(String key, Object def) {

        Object o = parameters.get(key);
        return (o == null) ? def : o;
    }

    public static void setParam(NamedList args, String key, Object def) {

        Object value = args.get(key);
        if (value == null)
            value = def;
        Utils.setParam(key, value);
    }

    public static void setParam(String key, Object o) {

        if (o == null)
            parameters.remove(key);
        else
            parameters.put(key, o);
    }

    public static void clearParams() {
        parameters.clear();
    }

    /**
     * formatDate
     * <p/>
     * Use the template to supply default date parameters to pad the given date in to a ISO 8601 date YYYY-mm-DDTHH:MM:SSZ format
     */
    public static String formatDate(String date) {

        String template = "YYYY-01-01T00:00:00Z";

        final int tl = template.length();
        final int dl = date.length();
        if (dl > tl)
            return date.substring(0, tl - 1) + "Z";
        else if (dl < tl)
            return date + template.substring(dl);
        return date;
    }

    /**
     * parseRange
     * <p/>
     *
     * @return An ISO 8601 formatted date or an infinite range Lucene character when the date is null
     */
    public static String parseRange(String date) {

        return (date == null) ? "*" : formatDate(date);
    }

    public static boolean isValidDatestamp(String datestamp, String range, SolrQueryResponse response) {

        return datestamp == null || datestampPattern.matcher(datestamp).matches() || error(response,
                String.format("The '%s' argument '%s' is not a valid UTCdatetime.", range, datestamp),
                OAIPMHerrorcodeType.BAD_ARGUMENT);
    }

    /**
     * stripOaiPrefix
     * <p/>
     * Remove the oai prefix and return the identifier.
     * oai:domain:identifier => identifier
     *
     * @param identifier the oai identifier
     * @return A local identifier
     */
    public static String stripOaiPrefix(String identifier) {

        final String prefix = (String) Utils.getParam("prefix");
        final String id = identifier.substring(prefix.length());
        System.out.println("Prefix is " + prefix);
        System.out.println("Identifier is " + identifier);
        System.out.println("Looking for " + id);
        return id;
    }

    /**
     * loadStaticVerb
     * <p/>
     * Loads an Identity.xml, ListSets.xml or ListMetadataPrefix.xml from file and unmarshalls it.
     *
     * @param verb The OAI2 verb
     * @return The OAIPMHtype instance
     * @throws FileNotFoundException
     * @throws JAXBException
     */
    public static OAIPMHtype loadStaticVerb(VerbType verb) throws FileNotFoundException, JAXBException {
        final File f = new File(Utils.getParam("oai_home") + File.separator + verb.value() + ".xml");
        final FileInputStream fis = new FileInputStream(f);
        final Source source = new StreamSource(fis);
        final Unmarshaller marshaller = (Unmarshaller) Utils.getParam("unmarshaller");
        return ((JAXBElement<OAIPMHtype>) marshaller.unmarshal(source)).getValue();
    }
}