/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.meet.test.base;

import io.github.bonigarcia.wdm.*;
import org.jitsi.meet.test.mobile.*;
import org.jitsi.meet.test.util.*;
import org.jitsi.meet.test.web.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.firefox.*;
import org.openqa.selenium.ie.*;
import org.openqa.selenium.logging.*;
import org.openqa.selenium.remote.*;
import org.openqa.selenium.safari.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeoutException;
import java.util.logging.*;

public class ParticipantFactory implements ParticipantFactoryConfig
{
    /**
     * The url of the deployment to connect to.
     */
    public static final String JITSI_MEET_URL_PROP = "jitsi-meet.instance.url";

    /**
     * The property to change remote selenium grid URL, defaults to
     * http://localhost:4444/wd/hub if requiring remote browser and property
     * is not set.
     */
    private static final String REMOTE_ADDRESS_NAME_PROP = "remote.address";

    /**
     * The property to disable no-sandbox parameter for chrome.
     */
    private static final String DISABLE_NOSANBOX_PARAM
        = "chrome.disable.nosanbox";

    /**
     * The property to enable headless parameter for chrome.
     */
    private static final String ENABLE_HEADLESS_PARAM
        = "chrome.enable.headless";

    /**
     * The property to evaluate for parent path of the resources used when
     * loading chrome remotely like audio/video files.
     */
    private static final String REMOTE_RESOURCE_PARENT_PATH_NAME_PROP
        = "remote.resource.path";

    public static final String FAKE_AUDIO_FNAME_PROP
        = "jitsi-meet.fakeStreamAudioFile";

    public static final String FAKE_VIDEO_FNAME_PROP
        = "jitsi-meet.fakeStreamVideoFile";

    /**
     * The test config.
     */
    private final Properties config;

    /**
     * Full name of wav file which will be streamed through participant's fake
     * audio device.
     * TODO: make this file non static and to be passed as a parameter.
     */
    private String fakeStreamAudioFName;

    /**
     * Full name of wav file which will be streamed through participant's fake
     * video device.
     * TODO: make this file non static and to be passed as a parameter.
     */
    private String fakeStreamVideoFName;

    /**
     * The private constructor of the factory.
     *
     * @param config - A <tt>Properties</tt> instance holding configuration
     * properties required to setup new participants.
     */
    ParticipantFactory(Properties config)
    {
        this.config = Objects.requireNonNull(config, "config");

        String fakeStreamAudioFile = System.getProperty(FAKE_AUDIO_FNAME_PROP);
        if (fakeStreamAudioFile == null)
        {
            fakeStreamAudioFile = "resources/fakeAudioStream.wav";
        }
        setFakeStreamAudioFile(fakeStreamAudioFile);

        String fakeStreamVideoFile = System.getProperty(FAKE_VIDEO_FNAME_PROP);
        if (fakeStreamVideoFile != null
            && fakeStreamVideoFile.trim().length() > 0)
        {
            setFakeStreamVideoFile(fakeStreamVideoFile.trim());
        }
    }

    /**
     * Return new {@link JitsiMeetUrl} instance which has only
     * {@link JitsiMeetUrl#serverUrl} field initialized with the value from
     * {@link #JITSI_MEET_URL_PROP} system property.
     *
     * @return a new instance of {@link JitsiMeetUrl}.
     */
    public static JitsiMeetUrl getJitsiMeetUrl()
    {
        JitsiMeetUrl url = new JitsiMeetUrl();

        url.setServerUrl(System.getProperty(JITSI_MEET_URL_PROP));

        return url;
    }

    /**
     * The configuration prefix to use for initializing the participant.
     *
     * @param configPrefix the config prefix.
     */
    public Participant<? extends WebDriver> createParticipant(
            String configPrefix)
    {
        // It will be Chrome by default...
        ParticipantType participantType
            =  ParticipantType.valueOfString(
                    System.getProperty(configPrefix + ".type"));

        if (participantType == null)
        {
            TestUtils.print(
                    "No participant type specified for prefix: "
                        + configPrefix+", will use Chrome...");
            participantType = ParticipantType.chrome;
        }

        String name = configPrefix.substring(configPrefix.indexOf('.') + 1);
        String serverUrl = getJitsiMeetUrl().getServerUrl();

        if (participantType.isWeb())
        {
            return new WebParticipant(
                    name,
                    startWebDriver(
                            name,
                            configPrefix,
                            participantType,
                            System.getProperty(configPrefix + ".version")),
                    participantType,
                    serverUrl);
        }
        else if (participantType.isMobile())
        {
            MobileParticipantBuilder builder
                = MobileParticipantBuilder.createBuilder(
                        config,
                        configPrefix,
                        participantType);

            return builder.startNewDriver(serverUrl);
        }
        else
        {
            throw new IllegalArgumentException(
                    participantType + " not supported");
        }
    }

    /**
     * Starts a <tt>WebDriver</tt> instance using default settings.
     * @param name the participant name.
     * @param configPrefix the configuration property to retrieve settings.
     * @param participantType the participant type we are creating a driver for.
     * @param version (optional) version of the browser
     * @return the <tt>WebDriver</tt> instance.
     */
    private WebDriver startWebDriver(
        String name,
        String configPrefix,
        ParticipantType participantType,
        String version)
    {
        boolean isRemote = Boolean.getBoolean(configPrefix + ".isRemote");
        String browserBinary
            = System.getProperty(configPrefix + ".binary");

        // by default we load chrome, but we can load safari or firefox
        if (participantType.isFirefox())
        {
            FirefoxDriverManager.getInstance().setup();

            if (browserBinary != null && browserBinary.trim().length() > 0)
            {
                File binaryFile = new File(browserBinary);
                if (binaryFile.exists())
                    System.setProperty("webdriver.firefox.bin", browserBinary);
            }

            FirefoxProfile profile = new FirefoxProfile();
            profile.setPreference("media.navigator.permission.disabled", true);
            // Enables tcp in firefox, disabled by default in 44
            profile.setPreference("media.peerconnection.ice.tcp", true);
            profile.setPreference("media.navigator.streams.fake", true);
            profile.setAcceptUntrustedCertificates(true);

            profile.setPreference(
                "webdriver.log.file", FailureListener.createLogsFolder() +
                "/firefox-js-console-"
                + name + ".log");

            System.setProperty("webdriver.firefox.logfile",
                FailureListener.createLogsFolder() +
                    "/firefox-console-"
                    + name + ".log");

            if (isRemote)
            {
                FirefoxOptions ffOptions = new FirefoxOptions();
                ffOptions.setProfile(profile);

                if (version != null && version.length() > 0)
                {
                    ffOptions.setCapability(CapabilityType.VERSION, version);
                }

                return new RemoteWebDriver(getRemoteDriverAddress(), ffOptions);
            }

            return new FirefoxDriver(new FirefoxOptions().setProfile(profile));
        }
        else if (participantType == ParticipantType.safari)
        {
            return new SafariDriver();
        }
        else if (participantType == ParticipantType.edge)
        {
            InternetExplorerDriverManager.getInstance().setup();

            InternetExplorerOptions ieOptions = new InternetExplorerOptions();
            ieOptions.ignoreZoomSettings();

            System.setProperty("webdriver.ie.driver.silent", "true");

            return new InternetExplorerDriver(ieOptions);
        }
        else
        {
            ChromeDriverManager.getInstance().setup();

            System.setProperty("webdriver.chrome.verboseLogging", "true");
            System.setProperty(
                    "webdriver.chrome.logfile",
                    FailureListener.createLogsFolder()
                        + "/chrome-console-" + name + ".log");

            LoggingPreferences logPrefs = new LoggingPreferences();
            logPrefs.enable(LogType.BROWSER, Level.ALL);

            final ChromeOptions ops = new ChromeOptions();
            ops.addArguments("use-fake-ui-for-media-stream");
            ops.addArguments("use-fake-device-for-media-stream");
            ops.addArguments("disable-extensions");
            ops.addArguments("disable-plugins");
            ops.addArguments("mute-audio");
            ops.addArguments("disable-infobars");

            ops.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);

            if (!Boolean.getBoolean(DISABLE_NOSANBOX_PARAM))
            {
                ops.addArguments("no-sandbox");
                ops.addArguments("disable-setuid-sandbox");
            }

            if (Boolean.getBoolean(ENABLE_HEADLESS_PARAM))
            {
                ops.addArguments("headless");
                ops.addArguments("window-size=1200x600");
            }

            // starting version 46 we see crashes of chrome GPU process when
            // running in headless mode
            // which leaves the browser opened and selenium hang forever.
            // There are reports that in older version crashes like that will
            // fallback to software graphics, we try to disable gpu for now
            ops.addArguments("disable-gpu");

            if (browserBinary != null && browserBinary.trim().length() > 0)
            {
                File binaryFile = new File(browserBinary);
                if (binaryFile.exists())
                    ops.setBinary(binaryFile);
            }

            String remoteResourcePath = System.getProperty(
                REMOTE_RESOURCE_PARENT_PATH_NAME_PROP);

            if (fakeStreamAudioFName != null)
            {
                String fileAbsolutePath = new File(
                    isRemote && remoteResourcePath != null
                        ? new File(remoteResourcePath) : null,
                    fakeStreamAudioFName).getAbsolutePath();

                ops.addArguments(
                    "use-file-for-fake-audio-capture=" + fileAbsolutePath);
            }

            if (fakeStreamVideoFName != null)
            {
                String fileAbsolutePath = new File(
                    isRemote && remoteResourcePath != null
                        ? new File(remoteResourcePath) : null,
                    fakeStreamVideoFName).getAbsolutePath();

                ops.addArguments(
                    "use-file-for-fake-video-capture=" + fileAbsolutePath);
            }

            //ops.addArguments("vmodule=\"*media/*=3,*turn*=3\"");
            ops.addArguments("enable-logging");
            ops.addArguments("vmodule=*=3");

            if (isRemote)
            {
                if (version != null && version.length() > 0)
                {
                    ops.setCapability(CapabilityType.VERSION, version);
                }

                return new RemoteWebDriver(getRemoteDriverAddress(), ops);
            }

            try
            {
                final ExecutorService pool = Executors.newFixedThreadPool(1);
                // we will retry four times for 1 minute to obtain
                // the chrome driver, on headless environments chrome hangs
                // and we wait forever
                for (int i = 0; i < 2; i++)
                {
                    Future<ChromeDriver> future = null;
                    try
                    {
                        future = pool.submit(
                            () -> {
                                long start = System.currentTimeMillis();
                                ChromeDriver resDr = new ChromeDriver(ops);
                                TestUtils.print(
                                    "ChromeDriver created for:"
                                        + (System.currentTimeMillis() - start)
                                        + " ms.");
                                return resDr;
                            });

                        ChromeDriver res = future.get(2, TimeUnit.MINUTES);
                        if (res != null)
                            return res;
                    }
                    catch (TimeoutException te)
                    {
                        // cancel current task
                        future.cancel(true);

                        TestUtils.print("Timeout waiting for "
                            + "chrome instance! We will retry now, this was our"
                            + "attempt " + i);
                    }

                }
            }
            catch (InterruptedException | ExecutionException e)
            {
                e.printStackTrace();
            }

            // keep the old code
            TestUtils.print("Just create ChromeDriver, may hang!");
            return new ChromeDriver(ops);
        }
    }

    /**
     * Returns the remote driver address or the default one.
     * @return the remote driver address or the default one.
     */
    private static URL getRemoteDriverAddress()
    {
        try
        {
            String remoteAddress = System.getProperty(
                REMOTE_ADDRESS_NAME_PROP,
                "http://localhost:4444/wd/hub");

            return new URL(remoteAddress);
        }
        catch (MalformedURLException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    public void setFakeStreamAudioFile(String fakeStreamAudioFile)
    {
        fakeStreamAudioFName = fakeStreamAudioFile;
    }

    /**
     * {@inheritDoc}
     */
    public void setFakeStreamVideoFile(String fakeStreamVideoFile)
    {
        fakeStreamVideoFName = fakeStreamVideoFile;
    }
}
