package com.tanaw.app.di

import com.tanaw.app.data.repository.AuthRepository
import com.tanaw.app.data.repository.LocationRepository
import com.tanaw.app.data.repository.SupabaseAuthRepositoryImpl
import com.tanaw.app.data.repository.SupabaseBookingsRepositoryImpl
import com.tanaw.app.data.repository.SupabaseDriverRepositoryImpl
import com.tanaw.app.data.repository.SupabaseLocationRepositoryImpl
import com.tanaw.app.data.repository.SupabaseUserRepositoryImpl
import com.tanaw.app.data.repository.UserRepository
import com.tanaw.app.domain.repository.BookingsRepository
import com.tanaw.app.domain.repository.DriverRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: SupabaseAuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        impl: SupabaseUserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        impl: SupabaseLocationRepositoryImpl
    ): LocationRepository

    @Binds
    @Singleton
    abstract fun bindDriverRepository(
        impl: SupabaseDriverRepositoryImpl
    ): DriverRepository

    @Binds
    @Singleton
    abstract fun bindBookingsRepository(
        impl: SupabaseBookingsRepositoryImpl
    ): BookingsRepository
}
