/*
 * This file is part of ViaForge - https://github.com/ViaVersion/ViaForge
 * Copyright (C) 2021-2026 Florian Reuth <git@florianreuth.de> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.github.seraphina.nyx.client.via.common.platform;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.util.Config;
import java.io.File;
import java.net.URL;
import java.util.logging.Logger;

public class ViaForgeConfig extends Config {

    public static final String CLIENT_SIDE_VERSION = "client-side-version";
    public static final String VERIFY_SESSION_IN_OLD_VERSIONS = "verify-session-in-old-versions";
    public static final String ALLOW_BETACRAFT_AUTHENTICATION = "allow-betacraft-authentication";
    public static final String SHOW_PROTOCOL_VERSION_IN_F3 = "show-protocol-version-in-f3";

    /**
     * @param configFile The location of where the config is loaded/saved.
     */
    public ViaForgeConfig(File configFile, Logger logger) {
        super(configFile, logger);
        reload();
    }

    @Override
    public URL getDefaultConfigURL() {
        return getClass().getClassLoader().getResource("assets/nyxclient/via/config.yml");
    }

    @Override
    public void set(String path, Object value) {
        super.set(path, value);
        save(); // Automatically save the config when something changes
    }

    public String getClientSideVersion() {
        if (getInt(CLIENT_SIDE_VERSION, -1) != -1) { // Temporary fix for old configs
            return ProtocolVersion.getProtocol(getInt(CLIENT_SIDE_VERSION, -1)).getName();
        }
        return getString(CLIENT_SIDE_VERSION, "");
    }

    public void setClientSideVersion(final String version) {
        set(CLIENT_SIDE_VERSION, version);
    }

    public boolean isVerifySessionInOldVersions() {
        return getBoolean(VERIFY_SESSION_IN_OLD_VERSIONS, true);
    }

    public boolean isAllowBetacraftAuthentication() {
        return getBoolean(ALLOW_BETACRAFT_AUTHENTICATION, true);
    }

    public boolean isShowProtocolVersionInF3() {
        return getBoolean(SHOW_PROTOCOL_VERSION_IN_F3, true);
    }

}
