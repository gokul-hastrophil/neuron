package ai.neuron.character.di

import ai.neuron.character.CharacterEngine
import ai.neuron.character.dao.CharacterDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CharacterScope

@Module
@InstallIn(SingletonComponent::class)
object CharacterModule {
    @Provides
    @Singleton
    @CharacterScope
    fun provideCharacterScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideCharacterEngine(
        dao: CharacterDao,
        @CharacterScope scope: CoroutineScope,
    ): CharacterEngine = CharacterEngine(dao, scope)
}
