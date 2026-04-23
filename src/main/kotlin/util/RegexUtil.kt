package org.burgas.util

class RegexUtil {

    companion object {
        val EMAIL: Regex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}