package me.zeroeightsix.kami.event.events;

import me.zero.alpine.type.EventState;
import me.zeroeightsix.kami.event.KamiEvent;

public class TravelEvent extends KamiEvent {
    private EventState eventState;

    public TravelEvent(EventState eventState) {
        this.eventState = eventState;
    }

    public EventState getEventState() {
        return eventState;
    }
}
