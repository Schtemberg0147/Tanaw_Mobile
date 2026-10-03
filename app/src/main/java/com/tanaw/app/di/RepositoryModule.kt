package com.tanaw.app.di

import com.tanaw.app.data.repository.AuthRepository
import com.tanaw.app.data.repository.SupabaseAuthRepositoryImpl
import com.tanaw.app.data.repository.SupabaseUserRepositoryImpl
import com.tanaw.app.data.repository.UserRepository
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
}