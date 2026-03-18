package abm.models.teleworkAdaption;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads 4-alternative telework coefficients from:
 *
 *   coefficient,NoOneTelework,OnlyMaleTelework,OnlyFemaleTelework,BothTelework
 */
public class TeleworkCoefficientsReader {

    private final Path path;

    public TeleworkCoefficientsReader(Path path) {
        this.path = path;
    }

    public Map<String, EnumMap<TeleworkAlternative, Double>> readFourAlternativeCoefficients() {
        Map<String, EnumMap<TeleworkAlternative, Double>> out = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {

            String header = nextNonEmpty(br);
            if (header == null) {
                throw new IllegalArgumentException("telework.coef is empty: " + path);
            }

            String[] columns = splitCsv(header);
            if (!(columns.length >= 5
                    && columns[0].equals("coefficient")
                    && columns[1].equalsIgnoreCase("NoOneTelework")
                    && columns[2].equalsIgnoreCase("OnlyMaleTelework")
                    && columns[3].equalsIgnoreCase("OnlyFemaleTelework")
                    && columns[4].equalsIgnoreCase("BothTelework"))) {
                throw new IllegalArgumentException(
                        "Header must be coefficient,NoOneTelework,OnlyMaleTelework,OnlyFemaleTelework,BothTelework"
                );
            }

            String line;
            while ((line = br.readLine()) != null) {
                line = trimComment(line);
                if (line.isBlank()) continue;

                String[] parts = splitCsv(line);
                if (parts.length < 5) continue;

                String variable = parts[0].trim();

                EnumMap<TeleworkAlternative, Double> row =
                        new EnumMap<>(TeleworkAlternative.class);

                row.put(TeleworkAlternative.NO_ONE,      parse(parts[1]));
                row.put(TeleworkAlternative.ONLY_MALE,   parse(parts[2]));
                row.put(TeleworkAlternative.ONLY_FEMALE, parse(parts[3]));
                row.put(TeleworkAlternative.BOTH,        parse(parts[4]));

                out.put(variable, row);
            }

        } catch (IOException e) {
            throw new RuntimeException("Error reading telework coefficients: " + path, e);
        }

        return out;
    }

    private static String nextNonEmpty(BufferedReader br) throws IOException {
        String line;
        while ((line = br.readLine()) != null) {
            line = trimComment(line);
            if (!line.isBlank()) return line;
        }
        return null;
    }

    private static String trimComment(String s) {
        int i = s.indexOf('#');
        return (i >= 0 ? s.substring(0, i) : s).trim();
    }

    private static String[] splitCsv(String s) {
        return s.split(",", -1);
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}