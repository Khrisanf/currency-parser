package ru.netology.currencyparser.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CbrClient {

    private final RestTemplate rt = new RestTemplate();

    @Value("${cbr.url}") private String cbrUrl;

    public static record CbrRate(String code, String name, int nominal, BigDecimal rate, LocalDate date) {}

    public List<CbrRate> fetchDaily(Optional<LocalDate> dateOpt) {
        try {
            String url = cbrUrl + dateOpt.map(d -> "?date_req=" + d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).orElse("");
            ResponseEntity<byte[]> resp = rt.getForEntity(url, byte[].class);
            String xml = new String(resp.getBody(), Charset.forName("windows-1251"));

            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(new InputSource(new StringReader(xml)));

            Element root = doc.getDocumentElement();
            LocalDate asOf = LocalDate.parse(root.getAttribute("Date"), DateTimeFormatter.ofPattern("dd.MM.yyyy"));

            NodeList nodes = root.getElementsByTagName("Valute");
            List<CbrRate> out = new ArrayList<>();
            for (int i=0;i<nodes.getLength();i++) {
                Element v = (Element) nodes.item(i);
                String code = text(v, "CharCode");
                String name = text(v, "Name");
                int nominal  = Integer.parseInt(text(v, "Nominal"));
                BigDecimal rate = new BigDecimal(text(v, "Value").replace(',', '.'));
                out.add(new CbrRate(code, name, nominal, rate, asOf));
            }
            return out;
        } catch (Exception e) {
            log.error("CBR fetch failed", e);
            return List.of();
        }
    }

    private static String text(Element e, String tag) {
        return ((Element)e.getElementsByTagName(tag).item(0)).getTextContent().trim();
    }
}
