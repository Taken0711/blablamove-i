package fr.unice.polytech.al.teamf.components;

import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import fr.unice.polytech.al.teamf.IntegrationTest;
import fr.unice.polytech.al.teamf.PullNotifications;
import fr.unice.polytech.al.teamf.entities.GPSCoordinate;
import fr.unice.polytech.al.teamf.entities.Parcel;
import fr.unice.polytech.al.teamf.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DataJpaTest
@ExtendWith(SpringExtension.class)
@Import({FindDriverBean.class, UserNotifierBean.class})
@AutoConfigureWireMock(port = 5000)
class FindDriverBeanIntegrationTest extends IntegrationTest {

    @Autowired
    private FindDriverBean driverFinder;
    @Autowired
    private PullNotifications pullNotifications;

    @BeforeEach
    void setUp() {
        driverFinder.route_finder_url = "http://localhost:5000";

        Map<String, StringValuePattern> params = new HashMap<>();
        StringValuePattern number = matching("[+-]?([0-9]*[.])?[0-9]+");
        params.put("start_lat", number);
        params.put("start_long", number);
        params.put("end_lat", number);
        params.put("end_long", number);
        stubFor(get(urlPathEqualTo("/find_driver")).withQueryParams(params).willReturn(aResponse()
                .withBody("{\"drivers\":[{\"name\":\"Erick\"}]}").withStatus(200)));
    }

    @Test
    void shouldNotifyOwnersWhenANewDriverHasBeenFound() {

        // We don't care about coordinates here
        GPSCoordinate gps = new GPSCoordinate(10, 20);

        User philippe = createAndSaveUser("Philippe");
        User benjamin = createAndSaveUser("Benjamin");
        // Get the mocked new transporter
        Parcel parcel = createAndSaveParcel(philippe);
        parcel.setKeeper(benjamin);
        User newDriver = driverFinder.findNewDriver(benjamin, parcel, gps, gps);
        assertThat(pullNotifications.pullNotificationForUser(philippe))
                .asList()
                .extracting("message")
                .hasSize(1)
                .contains(FindDriverBean.buildOwnerMessage("Erick"));
        assertThat(pullNotifications.pullNotificationForUser(benjamin))
                .asList()
                .extracting("message")
                .hasSize(1)
                .contains(FindDriverBean.buildCurrentDriverMessage("Erick", "Philippe"));
        assertThat(pullNotifications.pullNotificationForUser(newDriver))
                .asList()
                .extracting("message")
                .hasSize(1)
                .contains(FindDriverBean.buildNewDriverMessage("Benjamin", "Philippe"));
    }
}