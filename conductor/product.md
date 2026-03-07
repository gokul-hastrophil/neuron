# Product Definition - Neuron

## Project Name

Neuron

## Description

An AI-powered Android agent that autonomously controls your phone through natural language and voice commands.

## Problem Statement

Users must manually navigate complex app UIs to complete simple tasks — Neuron automates this with AI. There's no easy way to control your entire Android phone hands-free through natural language.

## Target Users

- **End users**: People who want hands-free, AI-driven phone control for daily tasks (messaging, navigation, settings, app control)
- **Developers**: Engineers building AI-powered automation on top of Android via the Neuron SDK and MCP server

## Key Goals

1. **Reliable UI control** via AccessibilityService — read any app's UI, execute taps/types/swipes
2. **Privacy-first hybrid AI** — sensitive data (passwords, banking) stays on-device (T4 tier), cloud for complex reasoning
3. **Developer-extensible** via MCP server and Kotlin/Python SDK for custom tool registration
4. **Ship a working MVP in 3 weeks** — Week 1: Nerve (AccessibilityService), Week 2: Brain (LLM routing), Week 3: Memory + SDK + Ship
5. **>70% task success rate** on 10-app integration benchmark (WhatsApp, Chrome, Settings, Contacts, Gmail, YouTube, Maps, Calendar, Camera, Play Store)
6. **Zero cloud leaks of sensitive data** — enforced by SensitivityGate with T4 routing

## Success Criteria

- End-to-end task execution: voice/text command → plan → execute → verify → done
- Works across any installed Android app without app-specific integrations
- APK size < 50MB, idle RAM < 80MB
- Simple task completion < 15s, complex task < 60s
