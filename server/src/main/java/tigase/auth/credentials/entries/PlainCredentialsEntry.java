/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.auth.credentials.entries;

import tigase.auth.CredentialsDecoderBean;
import tigase.auth.CredentialsEncoderBean;
import tigase.auth.credentials.Credentials;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.config.ConfigField;
import tigase.xmpp.BareJID;

public class PlainCredentialsEntry
		implements Credentials.Entry {

	private final String password;

	public PlainCredentialsEntry(String password) {
		this.password = password;
	}
	
	public String getPassword() {
		return password;
	}

	@Override
	public String getMechanism() {
		return "PLAIN";
	}

	@Override
	public boolean verifyPlainPassword(String plain) {
		return password == plain || password.equals(plain);
	}

	@Bean(name = "PLAIN", parent = CredentialsEncoderBean.class, active = true)
	public static class Encoder implements Credentials.Encoder {

		@ConfigField(desc = "Mechanism name")
		private String name;

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String encode(BareJID user, String password) {
			return password;
		}
	}

	@Bean(name = "PLAIN", parent = CredentialsDecoderBean.class, active = true)
	public static class Decoder implements Credentials.Decoder {

		@ConfigField(desc = "Mechanism name")
		private String name;

		@Override
		public String getName() {
			return name;
		}

		@Override
		public Credentials.Entry decode(BareJID user, String value) {
			return new PlainCredentialsEntry(value);
		}
	}
}