package airplane;

import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

class AircraftTest {

    private Engine engine;
    private FuelSystem fuelSystem;
    private LandingGear landingGear;
    private NavigationSystem navigationSystem;
    private Aircraft aircraft;

    @BeforeEach
    void setUp() {
        engine = mock(Engine.class);
        fuelSystem = mock(FuelSystem.class);
        landingGear = mock(LandingGear.class);
        navigationSystem = mock(NavigationSystem.class);
        aircraft = new Aircraft(engine, fuelSystem, landingGear, navigationSystem);
    }

    private void stubHealthyComponents() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
    }

    private void bringToInFlight() {
        stubHealthyComponents();
        aircraft.prepareForTakeoff();
        aircraft.takeOff();
    }

    @Test
    void prepareForTakeoff_whenAllSystemsOk_becomesReadyAndChecksEveryComponent() {
        stubHealthyComponents();

        aircraft.prepareForTakeoff();

        assertEquals(FlightStatus.READY, aircraft.getStatus());
        verify(engine, times(1)).isOperational();
        verify(fuelSystem, times(1)).hasEnoughFuel();
        verify(navigationSystem, times(1)).isRouteAvailable();
    }

    @Test
    void prepareForTakeoff_whenEngineBroken_throwsAndDoesNotCheckFuelOrRoute() {
        when(engine.isOperational()).thenReturn(false);

        assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(fuelSystem, times(0)).hasEnoughFuel();
        verify(navigationSystem, times(0)).isRouteAvailable();
    }

    @Test
    void prepareForTakeoff_whenFuelCheckThrows_exceptionPropagatesAndStatusUnchanged() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenThrow(new IllegalStateException("sensor failure"));

        assertThrows(IllegalStateException.class, () -> aircraft.prepareForTakeoff());

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(navigationSystem, times(0)).isRouteAvailable();
    }

    @Test
    void takeOff_startsEngineAndRetractsGear() {
        stubHealthyComponents();
        doNothing().when(engine).start();
        doNothing().when(landingGear).retract();
        aircraft.prepareForTakeoff();

        aircraft.takeOff();

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(engine, times(1)).start();
        verify(landingGear, times(1)).retract();
    }

    @Test
    void takeOff_whenEngineStartFails_abortsAndWrapsCause() {
        stubHealthyComponents();
        doThrow(new IllegalStateException("cannot start")).when(engine).start();
        aircraft.prepareForTakeoff();

        FlightException ex = assertThrows(FlightException.class, () -> aircraft.takeOff());

        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        verify(landingGear, times(0)).retract();
    }

    @Test
    void cruise_whenEngineFailsMidFlight_emergencyLandsWithoutConsumingFuel() {
        when(engine.isOperational()).thenReturn(true, false);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
        aircraft.prepareForTakeoff();
        aircraft.takeOff();

        aircraft.cruise(10.0);

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
        verify(fuelSystem, times(0)).consumeFuel(anyDouble());
    }

    @Test
    void cruise_whenFuelConsumptionFails_emergencyLandsAndThrowsFlightException() {
        bringToInFlight();
        doThrow(new IllegalStateException("Not enough fuel."))
                .when(fuelSystem).consumeFuel(any(Double.class));

        FlightException ex = assertThrows(FlightException.class, () -> aircraft.cruise(500.0));

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        verify(fuelSystem, times(1)).consumeFuel(500.0);
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @Test
    void cruise_withNonPositiveFuel_throwsIllegalArgumentAndTouchesNothing() {
        bringToInFlight();

        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(0));
        assertThrows(IllegalArgumentException.class, () -> aircraft.cruise(-5));

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(fuelSystem, times(0)).consumeFuel(any(Double.class));
    }

    @Test
    void land_deploysGearStopsEngineAndSetsLanded() {
        bringToInFlight();

        aircraft.land();

        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }
    @Test
    void prepareAgainAfterLanding_withResetMocks_usesNewStubbing() {
        bringToInFlight();
        aircraft.land();

        reset(engine, fuelSystem, navigationSystem, landingGear);
        verify(engine, times(0)).isOperational();    

        when(engine.isOperational()).thenReturn(false);    

        assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(engine, times(1)).isOperational();
    }


}