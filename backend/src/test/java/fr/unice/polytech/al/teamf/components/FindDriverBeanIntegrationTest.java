package fr.unice.polytech.al.teamf.components;

import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import fr.unice.polytech.al.teamf.IntegrationTest;
import fr.unice.polytech.al.teamf.PullNotifications;
import fr.unice.polytech.al.teamf.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DataJpaTest
@ExtendWith(SpringExtension.class)
@AutoConfigureWireMock(port = 5000)
@Import({FindDriverBean.class, UserNotifierBean.class, FindPackageHostBean.class})
class FindDriverBeanIntegrationTest extends IntegrationTest {
    
    @Autowired
    private FindDriverBean driverFinder;
    @Autowired
    private PullNotifications pullNotifications;
    
    @BeforeEach
    void setUp() {
        IntegrationTest.setupDriverFinder(driverFinder);
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
        List<Notification> notifications = pullNotifications.pullNotificationForUser(newDriver);
        assertThat(notifications)
                .asList()
                .extracting("message")
                .hasSize(1)
                .contains(FindDriverBean.buildNewDriverMessage("Benjamin", "Philippe"));
        Mission mission = missionRepository.findById((Long) notifications.get(0).getAnswer().getParameters().get("missionId")).get();
        driverFinder.answerToPendingMission(mission, newDriver, true);
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

    }
}