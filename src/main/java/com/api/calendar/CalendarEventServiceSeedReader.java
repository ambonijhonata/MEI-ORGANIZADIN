package com.api.calendar;

import com.api.servicecatalog.Service;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;

final class CalendarEventServiceSeedReader {

    private CalendarEventServiceSeedReader() {
    }

    /* package */ static Seed read(final Service service) {
        return new Seed(
                readString(service, "getDescription"),
                readBigDecimal(service, "getValue")
        );
    }

    private static String readString(final Service service, final String methodName) {
        return (String) invoke(service, methodName);
    }

    private static BigDecimal readBigDecimal(final Service service, final String methodName) {
        return (BigDecimal) invoke(service, methodName);
    }

    private static Object invoke(final Service service, final String methodName) {
        try {
            return Service.class.getMethod(methodName).invoke(service);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
            throw new IllegalStateException("Unable to read service seed", exception);
        }
    }

    /* package */ record Seed(String description, BigDecimal value) {
    }
}
