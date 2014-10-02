/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.meet.test;

import junit.framework.*;

import org.openqa.selenium.*;

/**
 * This test will setup the conference and will end when both
 * participants are connected.
 * We order tests alphabetically and use the stage1,2,3... to order them.
 *
 * @author Damian Minkov
 */
public class SetupConference
    extends TestCase
{
    public SetupConference(String name)
    {
        super(name);
    }

    public static junit.framework.Test suite()
    {
        TestSuite suite = new TestSuite();

        suite.addTest(new SetupConference("startFocus"));
        suite.addTest(new SetupConference("checkFocusJoinRoom"));
        suite.addTest(new SetupConference("startSecondParticipant"));
        suite.addTest(new SetupConference("checkSecondParticipantJoinRoom"));

        return suite;
    }

    public void startFocus()
    {
        ConferenceFixture.startFocus();
    }

    public void checkFocusJoinRoom()
    {
        // first lets wait 10 secs to join
        checkParticipantToJoinRoom(ConferenceFixture.focus, 10);
    }

    private void checkParticipantToJoinRoom(WebDriver participant, long timeout)
    {
        TestUtils.waitsForBoolean(
            participant,
            "return connection.emuc.joined;",
            timeout);
    }

    public void startSecondParticipant()
    {
        ConferenceFixture.startSecondParticipant();
    }

    public void checkSecondParticipantJoinRoom()
    {
        checkParticipantToJoinRoom(ConferenceFixture.secondParticipant, 10);
    }
}
