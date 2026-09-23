package com.tanaw.app.di

import com.tanaw.app.data.repository.AuthRepository
import com.tanaw.app.data.repository.SupabaseAuthRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    // This is the ONE line you change later to switch from mock to real auth:
    //   fun bindAuthRepository(impl: SupabaseAuthRepositoryImpl): AuthRepository
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: SupabaseAuthRepositoryImpl
    ): AuthRepository
}