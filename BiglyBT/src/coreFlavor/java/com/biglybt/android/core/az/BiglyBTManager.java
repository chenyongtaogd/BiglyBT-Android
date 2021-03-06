/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.core.az;

import android.util.Log;

import androidx.annotation.NonNull;

import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.android.client.CorePrefs;
import com.biglybt.android.util.FileUtils;
import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.config.impl.TransferSpeedValidator;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.logging.*;
import com.biglybt.core.logging.impl.LoggerImpl;
import com.biglybt.core.security.SESecurityManager;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.PluginManagerDefaults;
import com.biglybt.util.Thunk;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * This class sets up and manages the Vuze Core.
 * 
 * Android specific calls should be avoided in this class
 */
public class BiglyBTManager
{

	private static final String UI_NAME = "ac"; // Android Core

	private static final boolean RCM_ENABLE = true; // tux has started using this

	private static final boolean LONG_TERM_STATS_ENABLE = false;

	private static final boolean SPEED_MANAGER_ENABLE = false;

	private static final boolean TAG_MANAGER_ENABLE = true; // tux has started using these

	private static final boolean IP_FILTER_ENABLE = false;

	private static final boolean UPNPMS_ENABLE = false;

	private static final boolean UPNPAV_PUBLISH_TO_LAN = false;

	private static final boolean SUBSCRIPTIONS_ENABLE = true; // tux has started using this, 2016/10/25

	private static final String TAG = "Core";

	@Thunk
	static final LogIDs[] DEBUG_CORE_LOGGING_TYPES = CorePrefs.DEBUG_CORE
			? new LogIDs[] {
				LogIDs.CORE
			} : null;

	private static class MyOutputStream
		extends OutputStream
	{
		protected final StringBuffer buffer = new StringBuffer(1024);

		@NonNull
		String lastLine = "";

		final int type;

		public MyOutputStream(int type) {
			this.type = type;
		}

		@Override
		public void write(int data) {
			char c = (char) data;

			if (c == '\n') {
				String s = buffer.toString();
				if (!lastLine.equals(s) && !s.startsWith("(HTTPLog")) { //NON-NLS
					Log.println(type, "System", s);
					lastLine = s;
				}
				buffer.setLength(0);
			} else if (c != '\r') {
				buffer.append(c);
			}
		}

		@SuppressWarnings("MethodDoesntCallSuperMethod")
		@Override
		public void write(@NonNull byte[] b, int off, int len) {
			for (int i = off; i < off + len; i++) {
				int d = b[i];
				if (d < 0)
					d += 256;
				write(d);
			}
		}
	}

	@Thunk
	final Core core;

	public BiglyBTManager(File core_root) {

		if (CoreFactory.isCoreAvailable()) {
			core = CoreFactory.getSingleton();
			if (CorePrefs.DEBUG_CORE) {
				Log.w(TAG,
						"Core already available, using. isStarted? " + core.isStarted());
			}

			if (!core.isStarted()) {
				coreInit();
			}
			return;
		}

		System.setProperty("bdecoder.new", "1");
		try {
			System.setProperty("android.os.build.version.release", //NON-NLS
					android.os.Build.VERSION.RELEASE);
			System.setProperty("android.os.build.version.sdk_int", //NON-NLS
					String.valueOf(android.os.Build.VERSION.SDK_INT));

		} catch (Throwable e) {

			System.err.println(
					"Not running in an Android environment, not setting associated system properties");
		}

		core_root.mkdirs();

		// core tries to access debug_1.log.  This normally isn't a problem, except
		// on some Android devices, accessing a file that doesn't exist (File.length)
		// spews warnings to stdout, which mess up out initialization phase
		File logs = new File(core_root, "logs"); //NON-NLS
		if (!logs.exists()) {
			logs.mkdirs();
			File boo = new File(logs, "debug_1.log"); //NON-NLS
			try {
				boo.createNewFile();
			} catch (IOException e) {
			}
		}

		if (DEBUG_CORE_LOGGING_TYPES != null
				&& DEBUG_CORE_LOGGING_TYPES.length == 0) {
			System.setProperty("DIAG_TO_STDOUT", "1");
			System.setProperty("log.missing.messages", "1");
		}

		System.setProperty("az.force.noncvs", "1");
		System.setProperty("skip.shutdown.nondeamon.check", "1");
		System.setProperty("skip.shutdown.fail.killer", "1");
		System.setProperty("skip.dns.spi.test", "1");
		System.setProperty("log.missing.messages", "1");
		System.setProperty("skip.loggers.enabled.cvscheck", "1");
		System.setProperty("skip.loggers.setforced", "1");

		System.setProperty(SystemProperties.SYSPROP_CONFIG_PATH,
				core_root.getAbsolutePath());
		System.setProperty(SystemProperties.SYSPROP_INSTALL_PATH,
				core_root.getAbsolutePath());
		System.setProperty("azureus.time.use.raw.provider", "1");

		System.setProperty("az.factory.platformmanager.impl",
				PlatformManagerImpl.class.getName());
		System.setProperty("az.factory.dnsutils.impl", DNSProvider.class.getName());
		System.setProperty("az.factory.internat.bundle",
				"com.biglybt.ui.android.internat.MessagesBundle");
		System.setProperty("az.factory.ClientRestarter.impl",
				ClientRestarterImpl.class.getName());

		if (!SUBSCRIPTIONS_ENABLE) {
			System.setProperty("az.factory.subscriptionmanager.impl", "");
		}

		System.setProperty("az.factory.devicemanager.impl", "");

		System.setProperty("az.thread.pool.naming.enable", "false");
		System.setProperty("az.xmwebui.skip.ssl.hack", "true");
		System.setProperty("az.logging.save.debug", "false");
		System.setProperty("az.logging.keep.ui.history", "false");

		COConfigurationManager.initialise();
		//COConfigurationManager.resetToDefaults();
		//COConfigurationManager.setParameter("Plugin.aercm.rcm.ui.enable", false);

		@NonNull
		final ConfigurationDefaults coreDefaults = ConfigurationDefaults.getInstance();

		fixupLogger();

		COConfigurationManager.setParameter("ui", UI_NAME);

		coreDefaults.addParameter("Save Torrent Files", true);

		new File(COConfigurationManager.getStringParameter(
				"Default save path")).mkdirs();
		new File(COConfigurationManager.getStringParameter(
				"General_sDefaultTorrent_Directory")).mkdirs();

		boolean ENABLE_LOGGING = false;

		COConfigurationManager.setParameter("Logger.Enabled", ENABLE_LOGGING);

		COConfigurationManager.setParameter("Logging Enable", ENABLE_LOGGING);
		COConfigurationManager.setParameter("Logging Dir", "C:\\temp");
		COConfigurationManager.setParameter("Logger.DebugFiles.Enabled", false);

		coreDefaults.addParameter("Start In Low Resource Mode", true);
		coreDefaults.addParameter("DHT.protocol.version.min", 51);
		coreDefaults.addParameter("network.tcp.enable_safe_selector_mode", false);

		coreDefaults.addParameter(
				TransferSpeedValidator.AUTO_UPLOAD_ENABLED_CONFIGKEY, false);
		coreDefaults.addParameter(
				TransferSpeedValidator.AUTO_UPLOAD_SEEDING_ENABLED_CONFIGKEY, false);
		coreDefaults.addParameter(TransferSpeedValidator.UPLOAD_CONFIGKEY, 25);
		coreDefaults.addParameter(TransferSpeedValidator.DOWNLOAD_CONFIGKEY, 0);

		coreDefaults.addParameter("tagmanager.enable", TAG_MANAGER_ENABLE);
		coreDefaults.addParameter("speedmanager.enable", SPEED_MANAGER_ENABLE);
		coreDefaults.addParameter("long.term.stats.enable", LONG_TERM_STATS_ENABLE);
		coreDefaults.addParameter("rcm.overall.enabled", RCM_ENABLE);

		coreDefaults.addParameter("Ip Filter Enabled", IP_FILTER_ENABLE);
		coreDefaults.addParameter("Ip Filter Banning Persistent", false); // user has no way of removing bans atm so don't persist them for safety

		// Ensure plugins are enabled..
		COConfigurationManager.setParameter("PluginInfo.aercm.enabled", true);
		COConfigurationManager.setParameter("PluginInfo.azutp.enabled", true);
		COConfigurationManager.setParameter("PluginInfo.azbpmagnet.enabled", true);
		COConfigurationManager.setParameter("PluginInfo.azbpupnp.enabled", true);

		if (UPNPMS_ENABLE) {
			COConfigurationManager.setParameter(
					"Plugin.azupnpav.upnpmediaserver.enable_publish",
					UPNPAV_PUBLISH_TO_LAN);
			COConfigurationManager.setParameter(
					"Plugin.azupnpav.upnpmediaserver.enable_upnp", false);
			COConfigurationManager.setParameter(
					"Plugin.azupnpav.upnpmediaserver.stream_port_upnp", false);
			COConfigurationManager.setParameter(
					"Plugin.azupnpav.upnpmediaserver.bind.use.default", false);
			COConfigurationManager.setParameter(
					"Plugin.azupnpav.upnpmediaserver.prevent_sleep", false);
			COConfigurationManager.setParameter("PluginInfo.azupnpav.enabled", true);
		} else {
			COConfigurationManager.setParameter("PluginInfo.azupnpav.enabled", false);
		}

		coreDefaults.addParameter("dht.net.cvs_v4.enable", false);
		coreDefaults.addParameter("dht.net.main_v6.enable", false);

		coreDefaults.addParameter("Listen.Port.Randomize.Enable", true);
		coreDefaults.addParameter("network.tcp.read.select.time", 500);
		coreDefaults.addParameter("network.tcp.read.select.min.time", 500);
		coreDefaults.addParameter("network.tcp.write.select.time", 500);
		coreDefaults.addParameter("network.tcp.write.select.min.time", 500);
		coreDefaults.addParameter("network.tcp.connect.select.time", 500);
		coreDefaults.addParameter("network.tcp.connect.select.min.time", 500);

		coreDefaults.addParameter("network.udp.poll.time", 100);

		coreDefaults.addParameter("network.utp.poll.time", 100);

		coreDefaults.addParameter("network.control.read.idle.time", 100);
		coreDefaults.addParameter("network.control.write.idle.time", 100);

		coreDefaults.addParameter("diskmanager.perf.cache.enable", true);
		coreDefaults.addParameter("diskmanager.perf.cache.size", 2);
		coreDefaults.addParameter("diskmanager.perf.cache.flushpieces", false);
		coreDefaults.addParameter("diskmanager.perf.cache.enable.read", false);

		coreDefaults.addParameter("diskmanager.perf.read.maxthreads", 2);
		coreDefaults.addParameter("diskmanager.perf.read.maxmb", 2);
		coreDefaults.addParameter("diskmanager.perf.write.maxthreads", 2);
		coreDefaults.addParameter("diskmanager.perf.write.maxmb", 2);

		coreDefaults.addParameter("diskmanager.hashchecking.strategy", 0);

		coreDefaults.addParameter("peermanager.schedule.time", 500);

		PluginManagerDefaults defaults = PluginManager.getDefaults();

		defaults.setDefaultPluginEnabled(PluginManagerDefaults.PID_BUDDY, false);
		defaults.setDefaultPluginEnabled(PluginManagerDefaults.PID_SHARE_HOSTER,
				false);
		defaults.setDefaultPluginEnabled(PluginManagerDefaults.PID_RSS, false);
		defaults.setDefaultPluginEnabled(PluginManagerDefaults.PID_NET_STATUS,
				false);

		preinstallPlugins();

		/*
		ConsoleInput.registerPluginCommand( ConsoleDebugCommand.class );
		*/

		// core set Plugin.DHT.dht.logging true boolean
		// core log on 'Distributed DB'

		core = CoreFactory.create();

		coreInit();
		// remove me
		SESecurityManager.getAllTrustingTrustManager();
	}

	private void fixupLogger() {
		// On some Android devices, File.delete, File.length will write to stdout/err
		// This causes our core logger to stackoverflow.
		// Hack by forcing Logger init here, and taking back stdout/err

		try {

			Logger.doRedirects(); // makes sure loggerImpl is there
			Field fLoggerImpl = Logger.class.getDeclaredField("loggerImpl");
			OurLoggerImpl ourLogger = new OurLoggerImpl();
			fLoggerImpl.setAccessible(true);
			fLoggerImpl.set(null, ourLogger);

			System.setErr(new PrintStream(new MyOutputStream(Log.ERROR)));
			System.setOut(new PrintStream(new MyOutputStream(Log.WARN)));

		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}

		try {
			Field diag_logger = Debug.class.getDeclaredField("diag_logger");
			diag_logger.setAccessible(true);
			Object diag_logger_object = diag_logger.get(null);
			Method setForced = AEDiagnosticsLogger.class.getDeclaredMethod(
					"setForced", boolean.class);

			setForced.setAccessible(true);
			setForced.invoke(diag_logger_object, false);
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	@Thunk
	void coreInit() {
		AEThread2.createAndStartDaemon("CoreInit", core::start);
	}

	@Thunk
	static void preinstallPlugins() {
		// Copy <assets>/plugins to <userpath>/plugins
		// (<userpath> is usually "<internal storage>/.biglybt")

		try {
			if (CorePrefs.DEBUG_CORE) {
				Log.d("Core", "unzip plugins.zip");
			}
			InputStream inputStream = BiglyBTApp.getContext().getAssets().open(
					"plugins.zip");
			FileUtils.unzip(inputStream, new File(SystemProperties.getUserPath()),
					false);
			if (CorePrefs.DEBUG_CORE) {
				Log.d("Core", "unzip plugins.zip done");
			}
		} catch (IOException e) {
			Log.e(TAG, "preinstallPlugins: ", e);
		}
	}

	/*
	private void checkUpdates() {
		PluginManager pm = core.getPluginManager();

		UpdateManager update_manager = pm.getDefaultPluginInterface().getUpdateManager();

		final UpdateCheckInstance checker = update_manager.createUpdateCheckInstance();

		checker.addListener(new UpdateCheckInstanceListener() {
			@Override
			public void cancelled(UpdateCheckInstance instance) {

			}

			@Override
			public void complete(UpdateCheckInstance instance) {
				Update[] updates = instance.getUpdates();

				for (Update update : updates) {

					System.out.println("Update available for '" + update.getName()
							+ "', new version = " + update.getNewVersion());

					String[] descs = update.getDescription();

					for (String desc : descs) {

						System.out.println("\t" + desc);
					}
				}

				checker.cancel();
			}
		});

		checker.start();
	}
	 */

	public Core getCore() {
		return (core);
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	public static class OurLoggerImpl
		extends LoggerImpl
	{
		@Override
		public void addListener(ILogEventListener aListener) {
		}

		@Override
		public void addListener(ILogAlertListener l) {
		}

		@Override
		public void allowLoggingToStdErr(boolean allowed) {
		}

		@Override
		public void doRedirects() {
		}

		@Override
		public PrintStream getOldStdErr() {
			return System.err;
		}

		@Override
		public void init() {
		}

		@Override
		public boolean isEnabled() {
			return DEBUG_CORE_LOGGING_TYPES != null;
		}

		@Override
		public void log(LogAlert alert) {
			if (alert == null) {
				return;
			}
			int type = alert.entryType == LogAlert.LT_ERROR ? Log.ERROR
					: alert.entryType == LogAlert.LT_INFORMATION ? Log.INFO : Log.WARN;
			Log.println(type, "LogAlert", alert.text);
			if (alert.details != null && alert.details.length() > 0) {
				Log.println(type, "LogAlert", alert.details);
			}
		}

		@Override
		public void log(LogEvent event) {
			log(event.logID, event.entryType, event.text, event.err);
		}

		private static void log(LogIDs logID, int entryType, String text,
				Throwable err) {
			if (DEBUG_CORE_LOGGING_TYPES == null) {
				return;
			}
			if (logID == null || text == null || text.startsWith("[UPnP Core]")) {
				return;
			}
			boolean found = DEBUG_CORE_LOGGING_TYPES.length == 0;
			if (!found) {
				for (LogIDs id : DEBUG_CORE_LOGGING_TYPES) {
					if (id == logID) {
						found = true;
						break;
					}
				}
				if (!found) {
					return;
				}
			}
			int type = entryType == LogEvent.LT_ERROR ? Log.ERROR
					: entryType == LogEvent.LT_INFORMATION ? Log.INFO : Log.WARN;
			Log.println(type, logID.toString(), text);
			if (err != null && entryType == LogEvent.LT_ERROR) {
				Log.e(logID.toString(), null, err);
			}
		}

		public OurLoggerImpl() {
		}

		@Override
		public void logTextResource(LogAlert alert) {
			if (alert == null) {
				return;
			}
			int type = alert.entryType == LogAlert.LT_ERROR ? Log.ERROR
					: alert.entryType == LogAlert.LT_INFORMATION ? Log.INFO : Log.WARN;
			Log.println(type, "LogAlert", MessageText.getString(alert.text));
			if (alert.details != null && alert.details.length() > 0) {
				Log.println(type, "LogAlert", alert.details);
			}
		}

		@Override
		public void logTextResource(LogAlert alert, String[] params) {
			int type = alert.entryType == LogAlert.LT_ERROR ? Log.ERROR
					: alert.entryType == LogAlert.LT_INFORMATION ? Log.INFO : Log.WARN;
			String text;
			if (MessageText.keyExists(alert.text)) {
				text = MessageText.getString(alert.text, params);
			} else {
				text = "!" + alert.text + "(" + Arrays.toString(params) + ")!";
			}
			if (alert.details != null && alert.details.length() > 0) {
				text += "\n" + alert.details;
			}
			Log.println(type, "LogAlert", text);
		}

		@Override
		public void logTextResource(LogEvent event) {
			log(event.logID, event.entryType, MessageText.getString(event.text),
					event.err);
		}

		@Override
		public void logTextResource(LogEvent event, String[] params) {
			String text;
			if (MessageText.keyExists(event.text)) {
				text = MessageText.getString(event.text, params);
			} else {
				text = "!" + event.text + "(" + Arrays.toString(params) + ")!";
			}
			log(event.logID, event.entryType, text, event.err);
		}

		@Override
		public void removeListener(ILogEventListener aListener) {
		}

		@Override
		public void removeListener(ILogAlertListener l) {
		}
	}

}
