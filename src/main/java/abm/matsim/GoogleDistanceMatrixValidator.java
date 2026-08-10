package abm.matsim;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class GoogleDistanceMatrixValidator {

    private static final String INPUT_CSV =
            "output/10pct-sample-size/00pct_10Iter_matsim_output/sample_50_home_work_agents.csv";

    private static final String OUTPUT_CSV =
            "output/10pct-sample-size/00pct_10Iter_matsim_output/google_validation_50_results.csv";

    private static final String GOOGLE_API_KEY =
            "ssss";

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {

        try (
                BufferedReader reader = new BufferedReader(new FileReader(INPUT_CSV));
                PrintWriter writer = new PrintWriter(new FileWriter(OUTPUT_CSV))
        ) {
            reader.readLine();

            writer.println(
                    "person_id,mode," +
                            "home_lat,home_lon,work_lat,work_lon," +
                            "matsim_travel_time_s,matsim_distance_m," +
                            "google_duration_s,google_distance_m," +
                            "tt_diff_s,tt_diff_min," +
                            "distance_diff_m,distance_diff_km," +
                            "google_status,google_error_message"
            );

            String line;
            int counter = 0;

            while ((line = reader.readLine()) != null) {

                counter++;

                String[] p = line.split(",");

                String personId = p[0];
                String mode = p[1];

                double homeLat = Double.parseDouble(p[6]);
                double homeLon = Double.parseDouble(p[7]);
                double workLat = Double.parseDouble(p[8]);
                double workLon = Double.parseDouble(p[9]);

                double matsimTravelTimeS = Double.parseDouble(p[10]);
                double matsimDistanceM = Double.parseDouble(p[11]);

                System.out.println("Query " + counter + ": " + personId);

                GoogleResult google = queryGoogle(
                        homeLat,
                        homeLon,
                        workLat,
                        workLon
                );

                double ttDiffS = Double.NaN;
                double ttDiffMin = Double.NaN;
                double distanceDiffM = Double.NaN;
                double distanceDiffKm = Double.NaN;

                if (google.ok) {
                    ttDiffS = matsimTravelTimeS - google.durationS;
                    ttDiffMin = ttDiffS / 60.0;

                    distanceDiffM = matsimDistanceM - google.distanceM;
                    distanceDiffKm = distanceDiffM / 1000.0;
                }

                writer.printf(
                        Locale.US,
                        "%s,%s,%.8f,%.8f,%.8f,%.8f," +
                                "%.3f,%.3f,%.3f,%.3f," +
                                "%.3f,%.3f,%.3f,%.3f,%s,%s%n",
                        personId,
                        mode,
                        homeLat,
                        homeLon,
                        workLat,
                        workLon,
                        matsimTravelTimeS,
                        matsimDistanceM,
                        google.durationS,
                        google.distanceM,
                        ttDiffS,
                        ttDiffMin,
                        distanceDiffM,
                        distanceDiffKm,
                        google.status,
                        cleanCsvText(google.errorMessage)
                );

                writer.flush();

                Thread.sleep(1000);
            }
        }

        System.out.println("Saved: " + OUTPUT_CSV);
    }

    private static GoogleResult queryGoogle(
            double homeLat,
            double homeLon,
            double workLat,
            double workLon
    ) {
        GoogleResult result = new GoogleResult();

        try {
            String origin = homeLat + "," + homeLon;
            String destination = workLat + "," + workLon;

            String url =
                    "https://maps.googleapis.com/maps/api/distancematrix/json"
                            + "?origins=" + encode(origin)
                            + "&destinations=" + encode(destination)
                            + "&mode=driving"
                            + "&units=metric"
                            + "&key=" + encode(GOOGLE_API_KEY);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            String body = response.body();

            JsonObject json =
                    JsonParser.parseString(body).getAsJsonObject();

            if (json.has("error_message")) {
                result.errorMessage = json.get("error_message").getAsString();
            }

            String apiStatus = json.get("status").getAsString();

            if (!apiStatus.equals("OK")) {
                result.ok = false;
                result.status = apiStatus;

                System.out.println("FAILED API RESPONSE:");
                System.out.println(body);

                return result;
            }

            JsonObject element = json
                    .getAsJsonArray("rows")
                    .get(0).getAsJsonObject()
                    .getAsJsonArray("elements")
                    .get(0).getAsJsonObject();

            String elementStatus = element.get("status").getAsString();

            if (!elementStatus.equals("OK")) {
                result.ok = false;
                result.status = elementStatus;

                if (element.has("error_message")) {
                    result.errorMessage = element.get("error_message").getAsString();
                }

                System.out.println("FAILED ELEMENT RESPONSE:");
                System.out.println(body);

                return result;
            }

            result.durationS = element
                    .getAsJsonObject("duration")
                    .get("value")
                    .getAsDouble();

            result.distanceM = element
                    .getAsJsonObject("distance")
                    .get("value")
                    .getAsDouble();

            result.ok = true;
            result.status = "OK";

        } catch (Exception e) {
            result.ok = false;
            result.status = "ERROR_" + e.getClass().getSimpleName();
            result.errorMessage = e.getMessage() == null ? "" : e.getMessage();
            e.printStackTrace();
        }

        return result;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String cleanCsvText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace(",", ";")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private static class GoogleResult {
        boolean ok = false;
        String status = "NOT_QUERIED";
        String errorMessage = "";

        double durationS = Double.NaN;
        double distanceM = Double.NaN;
    }
}