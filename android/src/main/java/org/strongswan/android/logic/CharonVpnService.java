/*
 * Copyright (C) 2012-2015 Tobias Brunner
 * HSR Hochschule fuer Technik Rapperswil
 * Copyright (C) 2018 Jason C.H
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

package org.strongswan.android.logic;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.support.v4.app.NotificationCompat;
import android.system.OsConstants;
import android.util.Log;

import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.data.VpnProfile.SelectedAppsHandling;
import org.strongswan.android.data.VpnType;
import org.strongswan.android.utils.IPRange;
import org.strongswan.android.utils.IPRangeSet;
import org.strongswan.android.utils.SettingsWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.UUID;

import io.flutter.plugin.common.EventChannel;
import io.xdea.fluttervpn.VPNStateHandler;


public class CharonVpnService extends VpnService implements Runnable {
    private static final String NOTIFICATION_CHANNEL = "org.strongswan.android.CharonVpnService.VPN_STATE_NOTIFICATION";
    public static final String LOG_FILE = "charon.log";
    public static final String DISCONNECT_ACTION = "org.strongswan.android.CharonVpnService.DISCONNECT";

    /**
     * Adapter for VpnService.Builder which is used to access it safely via JNI.
     * There is a corresponding C object to access it from native code.
     */
    private BuilderAdapter mBuilderAdapter = new BuilderAdapter();
    /* handler used to do changes in the main UI thread */
    private Handler mHandler;
    /* use a separate thread as main thread for charon */
    private Thread mConnectionHandler;

    // Profiles
    private VpnProfile mCurrentProfile;
    private VpnProfile mNextProfile;
    private volatile boolean mProfileUpdated;

    // Certificates
    private volatile String mCurrentCertificateAlias;

    // LogFile & AppDir for charon
    private String mLogFile;
    private String mAppDir;


    @Override
    public void onCreate() {
        /* Prepare LogFile & AppDir for charon */
        mLogFile = getFilesDir().getAbsolutePath() + File.separator + LOG_FILE;
        mAppDir = getFilesDir().getAbsolutePath();

        /* handler used to do changes in the main UI thread */
        mHandler = new Handler();

        /* use a separate thread as main thread for charon */
        mConnectionHandler = new Thread(this);

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (DISCONNECT_ACTION.equals(intent.getAction())) {
            setNextProfile(null);
            stopSelf();
        } else {
            Bundle bundle = intent.getExtras();
            VpnProfile profile = new VpnProfile();
            profile.setId(1);
            profile.setUUID(UUID.randomUUID());
            profile.setName(bundle.getString("address"));
            profile.setGateway(bundle.getString("address"));
            profile.setUsername(bundle.getString("username"));
            profile.setPassword(bundle.getString("password"));
            profile.setVpnType(VpnType.fromIdentifier("ikev2-eap"));
            profile.setSelectedAppsHandling(0);
            profile.setFlags(0);
            setNextProfile(profile);
            if (!mConnectionHandler.isAlive())
                mConnectionHandler.start();
        }

        return START_NOT_STICKY;
    }

    /* the system revoked the rights grated with the initial prepare() call.
     * called when the user clicks disconnect in the system's VPN dialog */
    @Override
    public void onRevoke() {
        setNextProfile(null);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        setNextProfile(null);
        stopSelf();
    }

    /**
     * Set the profile that is to be initiated next. Notify the handler thread.
     *
     * @param profile the profile to initiate
     */
    private void setNextProfile(VpnProfile profile) {
        synchronized (this) {
            this.mNextProfile = profile;
            mProfileUpdated = true;
            notifyAll();
        }
    }

    @Override
    public void run() {
        while (true) {
            synchronized (this) {
                try {
                    while (!mProfileUpdated)
                        wait();


                    mProfileUpdated = false;
                    stopCurrentConnection();
                    if (mNextProfile == null)
                        break;
                    else {
                        mCurrentProfile = mNextProfile;
                        mNextProfile = null;

                        /* store this in a separate (volatile) variable to avoid
                         * a possible deadlock during deinitialization */
                        mCurrentCertificateAlias = mCurrentProfile.getCertificateAlias();

                        SimpleFetcher.enable();
                        addNotification();
                        mBuilderAdapter.setProfile(mCurrentProfile);

                        if (initializeCharon(mBuilderAdapter, mLogFile, mAppDir, false)) {
                            Log.i("VPN/Charon", "Charon started");

                            /* this can happen if Always-on VPN is enabled with an incomplete profile */
                            if (mCurrentProfile.getVpnType().has(VpnType.VpnTypeFeature.USER_PASS) &&
                                    mCurrentProfile.getPassword() == null)
                                continue;

                            SettingsWriter writer = new SettingsWriter();
                            writer.setValue("global.language", Locale.getDefault().getLanguage());
                            writer.setValue("global.mtu", mCurrentProfile.getMTU());
                            writer.setValue("global.nat_keepalive", mCurrentProfile.getNATKeepAlive());
                            writer.setValue("global.rsa_pss", (mCurrentProfile.getFlags() & VpnProfile.FLAGS_RSA_PSS) != 0);
                            writer.setValue("global.crl", (mCurrentProfile.getFlags() & VpnProfile.FLAGS_DISABLE_CRL) == 0);
                            writer.setValue("global.ocsp", (mCurrentProfile.getFlags() & VpnProfile.FLAGS_DISABLE_OCSP) == 0);
                            writer.setValue("connection.type", mCurrentProfile.getVpnType().getIdentifier());
                            writer.setValue("connection.server", mCurrentProfile.getGateway());
                            writer.setValue("connection.port", mCurrentProfile.getPort());
                            writer.setValue("connection.username", mCurrentProfile.getUsername());
                            writer.setValue("connection.password", mCurrentProfile.getPassword());
                            writer.setValue("connection.local_id", mCurrentProfile.getLocalId());
                            writer.setValue("connection.remote_id", mCurrentProfile.getRemoteId());
                            writer.setValue("connection.certreq", (mCurrentProfile.getFlags() & VpnProfile.FLAGS_SUPPRESS_CERT_REQS) == 0);
                            writer.setValue("connection.strict_revocation", (mCurrentProfile.getFlags() & VpnProfile.FLAGS_STRICT_REVOCATION) != 0);
                            writer.setValue("connection.ike_proposal", mCurrentProfile.getIkeProposal());
                            writer.setValue("connection.esp_proposal", mCurrentProfile.getEspProposal());
                            initiate(writer.serialize());
                        } else {
                            Log.e("VPN/Charon", "Failed to start charon");
                            mCurrentProfile = null;
                        }
                    }
                } catch (InterruptedException ex) {
                    stopCurrentConnection();
                }
            }
        }
    }

    /**
     * Updates the state of the current connection.
     * Called via JNI by different threads (but not concurrently).
     *
     * @param status new state
     */
    public void updateStatus(int status) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(1, buildNotification());

        // Update state through event channel.
        EventChannel.EventSink sink = VPNStateHandler.Companion.getEventHandler();
        if(sink != null)
            sink.success(status);
    }

    /**
     * Stop any existing connection by deinitializing charon.
     */
    private void stopCurrentConnection() {
        synchronized (this) {
            if (mNextProfile != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mBuilderAdapter.setProfile(mNextProfile);
                mBuilderAdapter.establishBlocking();
            }

            if (mCurrentProfile != null) {
                SimpleFetcher.disable();
                deinitializeCharon();
                Log.i("VPN/Charon", "Charon stopped");
                mCurrentProfile = null;
                if (mNextProfile == null) {
                    /* only do this if we are not connecting to another profile */
                    removeNotification();
                    mBuilderAdapter.closeBlocking();
                }
            }
        }
    }

    /**
     * Build a notification matching the current state
     */
    private Notification buildNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
                .setSmallIcon(android.support.v7.appcompat.R.drawable.abc_ic_star_black_36dp)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        builder.setContentTitle("VPN Connected");
        return builder.build();
    }

    /**
     * ORIGIN
     * Create a notification channel for Android 8+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel;
            channel = new NotificationChannel(NOTIFICATION_CHANNEL, "VPN connection state",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Provides information about the VPN connection state and serves as permanent notification to keep the VPN service running in the background.");
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            channel.setShowBadge(false);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Add a permanent notification while we are connected to avoid the service getting killed by
     * the system when low on memory.
     */
    private void addNotification() {
        mHandler.post(() -> startForeground(1, buildNotification()));
    }

    /**
     * Remove the permanent notification.
     */
    private void removeNotification() {
        mHandler.post(() -> stopForeground(true));
    }

    /**
     * Adapter for VpnService.Builder which is used to access it safely via JNI.
     * There is a corresponding C object to access it from native code.
     */
    public class BuilderAdapter {
        private VpnProfile mProfile;
        private VpnService.Builder mBuilder;
        private BuilderCache mCache;
        private BuilderCache mEstablishedCache;
        private PacketDropper mDropper = new PacketDropper();

        public synchronized void setProfile(VpnProfile profile) {
            mProfile = profile;
            mBuilder = createBuilder(mProfile.getName());
            mCache = new BuilderCache(mProfile);
        }

        private VpnService.Builder createBuilder(String name) {
            VpnService.Builder builder = new CharonVpnService.Builder();
            builder.setSession(name);
            return builder;
        }

        public synchronized boolean addAddress(String address, int prefixLength) {
            try {
                mCache.addAddress(address, prefixLength);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean addDnsServer(String address) {
            try {
                mBuilder.addDnsServer(address);
                mCache.recordAddressFamily(address);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean addRoute(String address, int prefixLength) {
            try {
                mCache.addRoute(address, prefixLength);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean addSearchDomain(String domain) {
            try {
                mBuilder.addSearchDomain(domain);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        public synchronized boolean setMtu(int mtu) {
            try {
                mCache.setMtu(mtu);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return true;
        }

        private synchronized ParcelFileDescriptor establishIntern() {
            ParcelFileDescriptor fd;
            try {
                mCache.applyData(mBuilder);
                fd = mBuilder.establish();
                if (fd != null) {
                    closeBlocking();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }
            if (fd == null) {
                return null;
            }
            /* now that the TUN device is created we don't need the current
             * builder anymore, but we might need another when reestablishing */
            mBuilder = createBuilder(mProfile.getName());
            mEstablishedCache = mCache;
            mCache = new BuilderCache(mProfile);
            return fd;
        }

        public synchronized int establish() {
            ParcelFileDescriptor fd = establishIntern();
            return fd != null ? fd.detachFd() : -1;
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public synchronized void establishBlocking() {
            /* just choose some arbitrary values to block all traffic (except for what's configured in the profile) */
            mCache.addAddress("172.16.252.1", 32);
            mCache.addAddress("fd00::fd02:1", 128);
            mCache.addRoute("0.0.0.0", 0);
            mCache.addRoute("::", 0);
            /* set DNS servers to avoid DNS leak later */
            mBuilder.addDnsServer("8.8.8.8");
            mBuilder.addDnsServer("2001:4860:4860::8888");
            /* use blocking mode to simplify packet dropping */
            mBuilder.setBlocking(true);
            ParcelFileDescriptor fd = establishIntern();
            if (fd != null) {
                mDropper.start(fd);
            }
        }

        public synchronized void closeBlocking() {
            mDropper.stop();
        }

        public synchronized int establishNoDns() {
            ParcelFileDescriptor fd;

            if (mEstablishedCache == null) {
                return -1;
            }
            try {
                Builder builder = createBuilder(mProfile.getName());
                mEstablishedCache.applyData(builder);
                fd = builder.establish();
            } catch (Exception ex) {
                ex.printStackTrace();
                return -1;
            }
            if (fd == null) {
                return -1;
            }
            return fd.detachFd();
        }

        private class PacketDropper implements Runnable {
            private ParcelFileDescriptor mFd;
            private Thread mThread;

            public void start(ParcelFileDescriptor fd) {
                mFd = fd;
                mThread = new Thread(this);
                mThread.start();
            }

            public void stop() {
                if (mFd != null) {
                    try {
                        mThread.interrupt();
                        mThread.join();
                        mFd.close();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mFd = null;
                }
            }

            @Override
            public synchronized void run() {
                try {
                    FileInputStream plain = new FileInputStream(mFd.getFileDescriptor());
                    ByteBuffer packet = ByteBuffer.allocate(mCache.mMtu);
                    while (true) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {    /* just read and ignore all data, regular read() is not interruptible */
                            int len = plain.getChannel().read(packet);
                            packet.clear();
                            if (len < 0) {
                                break;
                            }
                        } else {    /* this is rather ugly but on older platforms not even the NIO version of read() is interruptible */
                            boolean wait = true;
                            if (plain.available() > 0) {
                                int len = plain.read(packet.array());
                                packet.clear();
                                if (len < 0 || Thread.interrupted()) {
                                    break;
                                }
                                /* check again right away, there may be another packet */
                                wait = false;
                            }
                            if (wait) {
                                Thread.sleep(250);
                            }
                        }
                    }
                } catch (ClosedByInterruptException | InterruptedException e) {
                    /* regular interruption */
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Cache non DNS related information so we can recreate the builder without
     * that information when reestablishing IKE_SAs
     */
    public class BuilderCache {
        private final List<IPRange> mAddresses = new ArrayList<>();
        private final List<IPRange> mRoutesIPv4 = new ArrayList<>();
        private final List<IPRange> mRoutesIPv6 = new ArrayList<>();
        private final IPRangeSet mIncludedSubnetsv4 = new IPRangeSet();
        private final IPRangeSet mIncludedSubnetsv6 = new IPRangeSet();
        private final IPRangeSet mExcludedSubnets;
        private final int mSplitTunneling;
        private final SelectedAppsHandling mAppHandling;
        private final SortedSet<String> mSelectedApps;
        private int mMtu;
        private boolean mIPv4Seen, mIPv6Seen;

        public BuilderCache(VpnProfile profile) {
            IPRangeSet included = IPRangeSet.fromString(profile.getIncludedSubnets());
            for (IPRange range : included) {
                if (range.getFrom() instanceof Inet4Address) {
                    mIncludedSubnetsv4.add(range);
                } else if (range.getFrom() instanceof Inet6Address) {
                    mIncludedSubnetsv6.add(range);
                }
            }
            mExcludedSubnets = IPRangeSet.fromString(profile.getExcludedSubnets());
            Integer splitTunneling = profile.getSplitTunneling();
            mSplitTunneling = splitTunneling != null ? splitTunneling : 0;
            SelectedAppsHandling appHandling = profile.getSelectedAppsHandling();
            mSelectedApps = profile.getSelectedAppsSet();
            /* exclude our own app, otherwise the fetcher is blocked */
            switch (appHandling) {
                case SELECTED_APPS_DISABLE:
                    appHandling = SelectedAppsHandling.SELECTED_APPS_EXCLUDE;
                    mSelectedApps.clear();
                    /* fall-through */
                case SELECTED_APPS_EXCLUDE:
                    mSelectedApps.add(getPackageName());
                    break;
                case SELECTED_APPS_ONLY:
                    mSelectedApps.remove(getPackageName());
                    break;
            }
            mAppHandling = appHandling;

            /* set a default MTU, will be set by the daemon for regular interfaces */
            Integer mtu = profile.getMTU();
            mMtu = mtu == null ? 1500 : mtu;
        }

        public void addAddress(String address, int prefixLength) {
            try {
                mAddresses.add(new IPRange(address, prefixLength));
                recordAddressFamily(address);
            } catch (UnknownHostException ex) {
                ex.printStackTrace();
            }
        }

        public void addRoute(String address, int prefixLength) {
            try {
                if (isIPv6(address)) {
                    mRoutesIPv6.add(new IPRange(address, prefixLength));
                } else {
                    mRoutesIPv4.add(new IPRange(address, prefixLength));
                }
            } catch (UnknownHostException ex) {
                ex.printStackTrace();
            }
        }

        public void setMtu(int mtu) {
            mMtu = mtu;
        }

        public void recordAddressFamily(String address) {
            try {
                if (isIPv6(address)) {
                    mIPv6Seen = true;
                } else {
                    mIPv4Seen = true;
                }
            } catch (UnknownHostException ex) {
                ex.printStackTrace();
            }
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public void applyData(VpnService.Builder builder) {
            for (IPRange address : mAddresses) {
                builder.addAddress(address.getFrom(), address.getPrefix());
            }
            /* add routes depending on whether split tunneling is allowed or not,
             * that is, whether we have to handle and block non-VPN traffic */
            if ((mSplitTunneling & VpnProfile.SPLIT_TUNNELING_BLOCK_IPV4) == 0) {
                if (mIPv4Seen) {    /* split tunneling is used depending on the routes and configuration */
                    IPRangeSet ranges = new IPRangeSet();
                    if (mIncludedSubnetsv4.size() > 0) {
                        ranges.add(mIncludedSubnetsv4);
                    } else {
                        ranges.addAll(mRoutesIPv4);
                    }
                    ranges.remove(mExcludedSubnets);
                    for (IPRange subnet : ranges.subnets()) {
                        try {
                            builder.addRoute(subnet.getFrom(), subnet.getPrefix());
                        } catch (IllegalArgumentException e) {    /* some Android versions don't seem to like multicast addresses here,
                         * ignore it for now */
                            if (!subnet.getFrom().isMulticastAddress()) {
                                throw e;
                            }
                        }
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {    /* allow traffic that would otherwise be blocked to bypass the VPN */
                    builder.allowFamily(OsConstants.AF_INET);
                }
            } else if (mIPv4Seen) {    /* only needed if we've seen any addresses.  otherwise, traffic
             * is blocked by default (we also install no routes in that case) */
                builder.addRoute("0.0.0.0", 0);
            }
            /* same thing for IPv6 */
            if ((mSplitTunneling & VpnProfile.SPLIT_TUNNELING_BLOCK_IPV6) == 0) {
                if (mIPv6Seen) {
                    IPRangeSet ranges = new IPRangeSet();
                    if (mIncludedSubnetsv6.size() > 0) {
                        ranges.add(mIncludedSubnetsv6);
                    } else {
                        ranges.addAll(mRoutesIPv6);
                    }
                    ranges.remove(mExcludedSubnets);
                    for (IPRange subnet : ranges.subnets()) {
                        try {
                            builder.addRoute(subnet.getFrom(), subnet.getPrefix());
                        } catch (IllegalArgumentException e) {
                            if (!subnet.getFrom().isMulticastAddress()) {
                                throw e;
                            }
                        }
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    builder.allowFamily(OsConstants.AF_INET6);
                }
            } else if (mIPv6Seen) {
                builder.addRoute("::", 0);
            }
            /* apply selected applications */
            if (mSelectedApps.size() > 0 &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                switch (mAppHandling) {
                    case SELECTED_APPS_EXCLUDE:
                        for (String app : mSelectedApps) {
                            try {
                                builder.addDisallowedApplication(app);
                            } catch (PackageManager.NameNotFoundException e) {
                                // possible if not configured via GUI or app was uninstalled
                            }
                        }
                        break;
                    case SELECTED_APPS_ONLY:
                        for (String app : mSelectedApps) {
                            try {
                                builder.addAllowedApplication(app);
                            } catch (PackageManager.NameNotFoundException e) {
                                // possible if not configured via GUI or app was uninstalled
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
            builder.setMtu(mMtu);
        }

        private boolean isIPv6(String address) throws UnknownHostException {
            InetAddress addr = InetAddress.getByName(address);
            if (addr instanceof Inet4Address) {
                return false;
            } else if (addr instanceof Inet6Address) {
                return true;
            }
            return false;
        }
    }

    /**
     * ORIGIN
     * Function called via JNI to generate a list of DER encoded CA certificates
     * as byte array.
     *
     * @return a list of DER encoded CA certificates
     */
    private byte[][] getTrustedCertificates() {
        ArrayList<byte[]> certs = new ArrayList<byte[]>();
        TrustedCertificateManager certman = TrustedCertificateManager.getInstance().load();
        try {
            String alias = this.mCurrentCertificateAlias;
            if (alias != null) {
                X509Certificate cert = certman.getCACertificateFromAlias(alias);
                if (cert == null) {
                    return null;
                }
                certs.add(cert.getEncoded());
            } else {
                for (X509Certificate cert : certman.getAllCACertificates().values()) {
                    certs.add(cert.getEncoded());
                }
            }
        } catch (CertificateEncodingException e) {
            e.printStackTrace();
            return null;
        }
        return certs.toArray(new byte[certs.size()][]);
    }

    /**
     * ORIGIN
     * Function called via JNI to determine information about the Android version.
     */
    private static String getAndroidVersion() {
        String version = "Android " + Build.VERSION.RELEASE + " - " + Build.DISPLAY;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            version += "/" + Build.VERSION.SECURITY_PATCH;
        }
        return version;
    }

    /**
     * ORIGIN
     * Function called via JNI to determine information about the device.
     */
    private static String getDeviceString() {
        return Build.MODEL + " - " + Build.BRAND + "/" + Build.PRODUCT + "/" + Build.MANUFACTURER;
    }

    /**
     * ORIGIN
     * Initialization of charon, provided by libandroidbridge.so
     *
     * @param builder BuilderAdapter for this connection
     * @param logfile absolute path to the logfile
     * @param appdir  absolute path to the data directory of the app
     * @param byod    enable BYOD features
     * @return TRUE if initialization was successful
     */
    public native boolean initializeCharon(BuilderAdapter builder, String logfile, String appdir, boolean byod);

    /**
     * ORIGIN
     * Deinitialize charon, provided by libandroidbridge.so
     */
    public native void deinitializeCharon();

    /**
     * ORIGIN
     * Initiate VPN, provided by libandroidbridge.so
     */
    public native void initiate(String config);
}