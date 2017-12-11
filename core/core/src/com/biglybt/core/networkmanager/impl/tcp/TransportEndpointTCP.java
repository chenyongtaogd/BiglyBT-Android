/*
 * Created on 16 Jun 2006
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
 *
 */

package com.biglybt.core.networkmanager.impl.tcp;

import java.nio.channels.SocketChannel;

import com.biglybt.core.networkmanager.ProtocolEndpoint;
import com.biglybt.core.networkmanager.TransportEndpoint;

public class
TransportEndpointTCP
	implements TransportEndpoint
{
	private final ProtocolEndpoint		pe;
	private final SocketChannel			sc;

	public
	TransportEndpointTCP(
		ProtocolEndpoint	_pe,
		SocketChannel		_sc )
	{
		pe	= _pe;
		sc	= _sc;
	}

	@Override
	public ProtocolEndpoint
	getProtocolEndpoint()
	{
		return( pe );
	}

	public SocketChannel
	getSocketChannel()
	{
		return( sc );
	}
}
