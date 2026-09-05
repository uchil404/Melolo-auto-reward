package com.melolo.helper
import org.junit.Test
import org.junit.Assert.*
class MeloloResourceMapTest{
    @Test fun tomorrowNotClaim(){ assertNotEquals("check_in_task_button_check_in","check_in_task_button_tomorrow") }
    @Test fun unknownIsNotSuccess(){ assertNotEquals("UNKNOWN","SUCCESS") }
}
