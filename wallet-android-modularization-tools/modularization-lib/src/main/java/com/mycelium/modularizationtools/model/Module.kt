package com.mycelium.modularizationtools.model


class Module(val modulePackage: String, val name: String, val shortName: String, val description: String
             , val version: String = "") {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Module

        if (modulePackage != other.modulePackage) return false

        return true
    }

    override fun hashCode() = modulePackage.hashCode()
}