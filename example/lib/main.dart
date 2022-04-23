/// Copyright (C) 2018-2022 Jason C.H
///
/// This library is free software; you can redistribute it and/or
/// modify it under the terms of the GNU Lesser General Public
/// License as published by the Free Software Foundation; either
/// version 2.1 of the License, or (at your option) any later version.
///
/// This library is distributed in the hope that it will be useful,
/// but WITHOUT ANY WARRANTY; without even the implied warranty of
/// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
/// Lesser General Public License for more details.
import 'package:flutter/material.dart';
import 'package:flutter_vpn/flutter_vpn.dart';
import 'package:flutter_vpn/state.dart';

void main() => runApp(const MyApp());

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _addressController = TextEditingController();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();

  var state = FlutterVpnState.disconnected;
  CharonErrorState? charonState = CharonErrorState.NO_ERROR;

  @override
  void initState() {
    FlutterVpn.prepare();
    FlutterVpn.onStateChanged.listen((s) => setState(() => state = s));
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Flutter VPN'),
        ),
        body: ListView(
          padding: const EdgeInsets.all(12),
          children: <Widget>[
            Text('Current State: $state'),
            Text('Current Charon State: $charonState'),
            TextFormField(
              controller: _addressController,
              decoration: const InputDecoration(icon: Icon(Icons.map_outlined)),
            ),
            TextFormField(
              controller: _usernameController,
              decoration: const InputDecoration(
                icon: Icon(Icons.person_outline),
              ),
            ),
            TextFormField(
              controller: _passwordController,
              obscureText: true,
              decoration: const InputDecoration(icon: Icon(Icons.lock_outline)),
            ),
            ElevatedButton(
              child: const Text('Connect'),
              onPressed: () => FlutterVpn.connectIkev2EAP(
                server: _addressController.text,
                username: _usernameController.text,
                password: _passwordController.text,
              ),
            ),
            ElevatedButton(
              child: const Text('Disconnect'),
              onPressed: () => FlutterVpn.disconnect(),
            ),
            ElevatedButton(
              child: const Text('Update State'),
              onPressed: () async {
                var newState = await FlutterVpn.currentState;
                setState(() => state = newState);
              },
            ),
            ElevatedButton(
              child: const Text('Update Charon State'),
              onPressed: () async {
                var newState = await FlutterVpn.charonErrorState;
                setState(() => charonState = newState);
              },
            ),
          ],
        ),
      ),
    );
  }
}
