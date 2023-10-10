package abm.calibration;

import abm.data.DataSet;
import org.json.simple.JSONObject;

public interface ModelComponent {

    void setup();

    void load();

    void run();

}
