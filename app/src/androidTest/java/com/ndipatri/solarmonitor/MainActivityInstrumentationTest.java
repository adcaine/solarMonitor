package com.ndipatri.solarmonitor;

import android.content.Intent;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import com.ndipatri.solarmonitor.activities.MainActivity;
import com.ndipatri.solarmonitor.container.MockObjectGraph;
import com.ndipatri.solarmonitor.providers.panelScan.PanelInfo;
import com.ndipatri.solarmonitor.providers.solarUpdate.SolarOutputProvider;
import com.ndipatri.solarmonitor.providers.solarUpdate.dto.CurrentPower;
import com.ndipatri.solarmonitor.providers.solarUpdate.dto.GetOverviewResponse;
import com.ndipatri.solarmonitor.providers.solarUpdate.dto.LifeTimeData;
import com.ndipatri.solarmonitor.providers.solarUpdate.dto.Overview;
import com.ndipatri.solarmonitor.mocks.MockSolarOutputServer;
import com.ndipatri.solarmonitor.providers.panelScan.PanelScanProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.MalformedURLException;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.Single;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.PositionAssertions.isAbove;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.not;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class MainActivityInstrumentationTest {

    @Rule
    public ActivityTestRule<MainActivity> activityRule = new ActivityTestRule<>(MainActivity.class, true, false);

    @Rule
    public final AsyncTaskSchedulerRule asyncTaskSchedulerRule = new AsyncTaskSchedulerRule();

    @Inject
    SolarOutputProvider solarOutputProvider;
    @Inject
    PanelScanProvider panelScanProvider;

    @Before
    public void setUp() throws Exception {

        // Context of the app under test.
        SolarMonitorApp solarMonitorApp = (SolarMonitorApp) getInstrumentation().getTargetContext().getApplicationContext();

        // clear out any remaining state.
        solarMonitorApp.getSolarCustomerId().delete();
    }

    // Live Testing
    //
    // Here we use our production ObjectGraph with our real service and hardware layer.  This service
    // layer requires remote RESTful endpoints.  The hardware layer requires a real Bluetooth
    // stack to be present.
    //
    // Pros - no configuration, allows for automated 'live testing' and testing use real live system and data
    // Cons - live endpoint and hardware need to be in a known state.  If a test fails, your scope is so large
    // it doesn't really tell you much, necessarily, about your code itself.
    @Test
    public void retrieveSolarOutput_realHardware_realEndpoint() throws Exception {

        activityRule.launchActivity(new Intent());

        // NJD TODO - this works for now because our 'real' PanelScanProvider fakes a bluetooth
        // message from my home system.. in reality, i should be listening for real bluetooth
        // signals.. i need to do this still

        onView(withText("Click to find nearby Solar panel.")).check(matches(isDisplayed()));

        onView(withId(R.id.solarUpdateFAB)).check(matches(not(isDisplayed())));
        onView(withId(R.id.beaconScanFAB)).check(matches(isDisplayed()));
    }

    // Here we are injecting a 'mock' ObjectGraph which gives us the chance to mock out
    // some hardware components that our app depends upon.  The service layer in this
    // mock ObjectGraph is still the real implementation and therefore needs the MockWebServer.
    //
    // Pros - Allows us to run repeatable tests on a device that might not have all required production
    // components (e.g. emulators do not have Bluetooth).  This is useful for IOT testing.
    //
    // Cons - Can be incorrectly used to replace proper unit testing.  Unit tests are a
    // much faster way to test front-end components.  So we still use the real service
    // layer here!
    @Test
    public void retrieveSolarOutput_mockHardware_mockEndpoint() throws Exception {

        // Context of the app under test.
        SolarMonitorApp solarMonitorApp = (SolarMonitorApp) getInstrumentation().getTargetContext().getApplicationContext();

        // We can load the target application with a MockObjectGraph which will use real service
        // layer but a mock hardware layer.
        MockObjectGraph mockObjectGraph = MockObjectGraph.Initializer.init(solarMonitorApp);
        solarMonitorApp.setObjectGraph(mockObjectGraph);
        mockObjectGraph.inject(this);

        configureMockEndpoint(solarMonitorApp.getSolarCustomerId().get(), solarOutputProvider.getApiKey());
        configureMockHardware();

        activityRule.launchActivity(new Intent());

        /**
         * Ok, now to actually do some testing!
         */
        onView(withText("Click to find nearby Solar panel.")).check(matches(isDisplayed())).check(isAbove(withText("mock bluetooth found!")));

        onView(withId(R.id.solarUpdateFAB)).check(matches(isDisplayed())).perform(click());

        onView(withText("mock bluetooth found!")).check(matches(not(isDisplayed())));

        onView(withText("123.0 watts")).check(matches(isDisplayed()));
    }

    private void configureMockEndpoint(String solarCustomerId, String solarApiKey) throws MalformedURLException {
        // We deploy a MockWebServer to the same virtual machine as our
        // target APK
        MockSolarOutputServer mockSolarOutputServer = new MockSolarOutputServer();

        GetOverviewResponse getOverviewResponse = new GetOverviewResponse();

        CurrentPower currentPower = new CurrentPower();
        currentPower.setPower(123D);

        LifeTimeData lifeTimeData = new LifeTimeData();
        lifeTimeData.setEnergy(456D);

        Overview overview = new Overview();
        overview.setCurrentPower(currentPower);
        overview.setLifeTimeData(lifeTimeData);

        getOverviewResponse.setOverview(overview);

        mockSolarOutputServer.
                enqueueDesiredSolarOutputResponse(getOverviewResponse,
                        solarCustomerId,
                        solarApiKey);

        mockSolarOutputServer.beginUsingMockServer();

        // This is the only way in which our Test APK deviates from production.  We need to
        // point our service to the mock endpoint (mockWebServer)
        SolarOutputProvider.API_ENDPOINT_BASE_URL = mockSolarOutputServer.getMockSolarOutputServerURL();
    }

    private void configureMockHardware() {
        when(panelScanProvider.scanForNearbyPanel()).thenReturn(Observable.create(subscriber -> {
            subscriber.onNext(new PanelInfo("Nicks Solar Panels", "11111111"));
            subscriber.onComplete();
        }));
    }
}
