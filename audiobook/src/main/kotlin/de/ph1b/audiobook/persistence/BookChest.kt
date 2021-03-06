/*
 * This file is part of Material Audiobook Player.
 *
 * Material Audiobook Player is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 *
 * Material Audiobook Player is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 * /licenses/>.
 */

/*
 * This file is part of Material Audiobook Player.
 *
 * Material Audiobook Player is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 *
 * Material Audiobook Player is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 * /licenses/>.
 */

/*
 * This file is part of Material Audiobook Player.
 *
 * Material Audiobook Player is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 *
 * Material Audiobook Player is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 * /licenses/>.
 */

package de.ph1b.audiobook.persistence

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import de.ph1b.audiobook.model.Book
import de.ph1b.audiobook.model.Bookmark
import de.ph1b.audiobook.model.Chapter
import rx.Observable
import rx.subjects.PublishSubject
import timber.log.Timber
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton


/**
 * This is the helper for the apps database.

 * @author Paul Woitaschek
 */
@Singleton
class BookChest
@Inject
constructor(c: Context) {
    private val active: MutableList<Book> by lazy {
        synchronized(this) {
            val cursor = db.rawQuery("$FULL_PROJECTION $APPEND_WHERE_ACTIVE", arrayOf(BOOLEAN_TRUE.toString()))
            val active = ArrayList<Book>(cursor.count)
            cursor.moveToNextLoop {
                val book = byProjection(this)
                active.add(book)
            }
            active
        }
    }
    private val APPEND_WHERE_ACTIVE = " WHERE bt.${BookTable.ACTIVE} =?"
    private val orphaned: MutableList<Book> by lazy {
        synchronized(this) {
            val cursor = db.rawQuery("$FULL_PROJECTION $APPEND_WHERE_ACTIVE", arrayOf(BOOLEAN_FALSE.toString()))
            val active = ArrayList<Book>(cursor.count)
            cursor.moveToNextLoop {
                val book = byProjection(cursor)
                active.add(book)
            }
            active
        }
    }
    private val db: SQLiteDatabase by lazy {
        synchronized(this) {
            InternalDb(c).writableDatabase
        }
    }
    private val added = PublishSubject.create<Book>()
    private val removed = PublishSubject.create<Book>()
    private val updated = PublishSubject.create<Book>()

    private val BOOLEAN_TRUE = 1
    private val BOOLEAN_FALSE = 0
    private val KEY_CHAPTER_DURATIONS = "chapterDurations"
    private val KEY_CHAPTER_NAMES = "chapterNames"
    private val KEY_CHAPTER_PATHS = "chapterPaths"
    private val KEY_BOOKMARK_POSITIONS = "keyBookmarkPosition"
    private val KEY_BOOKMARK_TITLES = "keyBookmarkTitle"
    private val KEY_BOOKMARK_PATHS = "keyBookmarkPath"
    private val stringSeparator = "-~_"
    private val FULL_PROJECTION = "SELECT" +
            " bt." + BookTable.ID +
            ", bt." + BookTable.NAME +
            ", bt." + BookTable.AUTHOR +
            ", bt." + BookTable.CURRENT_MEDIA_PATH +
            ", bt." + BookTable.PLAYBACK_SPEED +
            ", bt." + BookTable.ROOT +
            ", bt." + BookTable.TIME +
            ", bt." + BookTable.TYPE +
            ", bt." + BookTable.USE_COVER_REPLACEMENT +
            ", bt." + BookTable.ACTIVE +
            ", ct." + KEY_CHAPTER_PATHS +
            ", ct." + KEY_CHAPTER_NAMES +
            ", ct." + KEY_CHAPTER_DURATIONS +
            ", bmt." + KEY_BOOKMARK_TITLES +
            ", bmt." + KEY_BOOKMARK_PATHS +
            ", bmt." + KEY_BOOKMARK_POSITIONS +
            " FROM " +
            BookTable.TABLE_NAME + " AS bt " +
            " left join" +
            "   (select " + ChapterTable.BOOK_ID + "," +
            "           group_concat(" + ChapterTable.PATH + ", '" + stringSeparator + "') as " + KEY_CHAPTER_PATHS + "," +
            "           group_concat(" + ChapterTable.DURATION + ") as " + KEY_CHAPTER_DURATIONS + "," +
            "           group_concat(" + ChapterTable.NAME + ", '" + stringSeparator + "') as " + KEY_CHAPTER_NAMES +
            "    from " + ChapterTable.TABLE_NAME +
            "    group by " + ChapterTable.BOOK_ID + ") AS ct on ct." + ChapterTable.BOOK_ID + " = bt." + BookTable.ID +
            " left join" +
            "    (select " + BookmarkTable.BOOK_ID + "," + "" +
            "            group_concat(" + BookmarkTable.TITLE + ", '" + stringSeparator + "') as " + KEY_BOOKMARK_TITLES + "," +
            "            group_concat(" + BookmarkTable.PATH + ", '" + stringSeparator + "') as " + KEY_BOOKMARK_PATHS + "," +
            "            group_concat(" + BookmarkTable.TIME + ") as " + KEY_BOOKMARK_POSITIONS +
            "     FROM " + BookmarkTable.TABLE_NAME +
            "     group by " + BookmarkTable.BOOK_ID + ") AS bmt on bmt." + BookmarkTable.BOOK_ID + " = bt." + BookTable.ID

    @Synchronized fun removedObservable(): Observable<Book> = removed.asObservable()

    @Synchronized fun addedObservable(): Observable<Book> = added.asObservable()

    @Synchronized fun updateObservable(): Observable<Book> = updated.asObservable()

    @Synchronized fun addBook(book: Book) {
        var newBook = book
        Timber.v("addBook=%s", newBook.name)

        db.asTransaction {
            val bookCv = BookTable.getContentValues(newBook)
            val bookId = insert(BookTable.TABLE_NAME, null, bookCv)

            newBook = newBook.copy(id = bookId)

            for (c in newBook.chapters) {
                val chapterCv = ChapterTable.getContentValues(c, newBook.id)
                insert(ChapterTable.TABLE_NAME, null, chapterCv)
            }

            for (b in newBook.bookmarks) {
                val bookmarkCv = BookmarkTable.getContentValues(b, newBook.id)
                insert(BookmarkTable.TABLE_NAME, null, bookmarkCv)
            }
        }

        active.add(newBook)
        added.onNext(newBook)
    }

    /**
     * All active books. We
     */
    val activeBooks = Observable.defer { Observable.from(synchronized(this) { ArrayList(active) }) }

    @Synchronized fun getOrphanedBooks(): List<Book> {
        return ArrayList(orphaned)
    }

    @Synchronized fun updateBook(book: Book) {
        Timber.v("updateBook=%s with time %d", book.name, book.time)

        val bookIterator = active.listIterator()
        while (bookIterator.hasNext()) {
            val next = bookIterator.next()
            if (book.id == next.id) {
                bookIterator.set(book)

                db.asTransaction {
                    // update book itself
                    val bookCv = BookTable.getContentValues(book)
                    update(BookTable.TABLE_NAME, bookCv, "${BookTable.ID}=?", arrayOf(book.id.toString()))

                    // delete old chapters and replace them with new ones
                    delete(ChapterTable.TABLE_NAME, "${BookTable.ID}=?", arrayOf(book.id.toString()))
                    for (c in book.chapters) {
                        val chapterCv = ChapterTable.getContentValues(c, book.id)
                        insert(ChapterTable.TABLE_NAME, null, chapterCv)
                    }

                    // replace old bookmarks and replace them with new ones
                    delete(BookmarkTable.TABLE_NAME, "${BookTable.ID}=?", arrayOf(book.id.toString()))
                    for (b in book.bookmarks) {
                        val bookmarkCV = BookmarkTable.getContentValues(b, book.id)
                        insert(BookmarkTable.TABLE_NAME, null, bookmarkCV)
                    }
                }

                break
            }
        }

        updated.onNext(book)
    }

    @Synchronized fun hideBook(book: Book) {
        Timber.v("hideBook=%s", book.name)

        val iterator = active.listIterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            if (next.id == book.id) {
                iterator.remove()
                val cv = ContentValues()
                cv.put(BookTable.ACTIVE, BOOLEAN_FALSE)
                db.update(BookTable.TABLE_NAME, cv, "${BookTable.ID}=?", arrayOf(book.id.toString()))
                break
            }
        }
        orphaned.add(book)
        removed.onNext(book)
    }

    @Synchronized fun revealBook(book: Book) {
        val orphanedBookIterator = orphaned.iterator()
        while (orphanedBookIterator.hasNext()) {
            if (orphanedBookIterator.next().id == book.id) {
                orphanedBookIterator.remove()
                val cv = ContentValues()
                cv.put(BookTable.ACTIVE, BOOLEAN_TRUE)
                db.update(BookTable.TABLE_NAME, cv, "${BookTable.ID}=?", arrayOf(book.id.toString()))
                break
            }
        }
        active.add(book)
        added.onNext(book)
    }

    private class InternalDb(context: Context) : SQLiteOpenHelper(context, BookChest.InternalDb.DATABASE_NAME, null, BookChest.InternalDb.DATABASE_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            BookTable.onCreate(db)
            ChapterTable.onCreate(db)
            BookmarkTable.onCreate(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            try {
                val upgradeHelper = DataBaseUpgradeHelper(db)
                upgradeHelper.upgrade(oldVersion)
            } catch (e: InvalidPropertiesFormatException) {
                Timber.e(e, "Error at upgrade")
                BookTable.dropTableIfExists(db)
                ChapterTable.dropTableIfExists(db)
                BookmarkTable.dropTableIfExists(db)
                onCreate(db)
            }
        }

        companion object {

            private val DATABASE_VERSION = 32
            private val DATABASE_NAME = "autoBookDB"
        }
    }


    private fun generateBookmarks(position: IntArray, path: Array<String>, title: Array<String>): List<Bookmark> {
        Preconditions.checkArgument(position.size == path.size && path.size == title.size,
                "Positions, path and title must have the same length but they are %d %d and %d",
                position.size, path.size, title.size)
        val length = position.size
        val bookmarks = ArrayList<Bookmark>(length)
        for (i in 0..length - 1) {
            bookmarks.add(Bookmark(File(path[i]), title[i], position[i]))
        }
        return bookmarks
    }

    private fun generateChapters(position: IntArray, path: Array<String>, title: Array<String>): List<Chapter> {
        Preconditions.checkArgument(position.size == path.size && path.size == title.size,
                "Positions, path and title must have the same length but they are %d %d and %d",
                position.size, path.size, title.size)
        val length = position.size
        val bookmarks = ArrayList<Chapter>(length)
        for (i in 0..length - 1) {
            bookmarks.add(Chapter(File(path[i]), title[i], position[i]))
        }
        return bookmarks
    }

    private fun convertToStringArray(from: Array<String>): IntArray {
        val out = IntArray(from.size)
        for (i in out.indices) {
            out[i] = Integer.valueOf(from[i])!!
        }
        return out
    }


    private fun byProjection(cursor: Cursor): Book {
        val rawDurations = cursor.string(KEY_CHAPTER_DURATIONS)
        val rawChapterNames = cursor.string(KEY_CHAPTER_NAMES)
        val rawChapterPaths = cursor.string(KEY_CHAPTER_PATHS)

        val chapterDurations = convertToStringArray(rawDurations.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        val chapterNames = rawChapterNames.split(stringSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val chapterPaths = rawChapterPaths.split(stringSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        val chapters = ImmutableList.copyOf(generateChapters(chapterDurations, chapterPaths, chapterNames)
                .sorted())

        val rawBookmarkPositions = cursor.stringNullable(KEY_BOOKMARK_POSITIONS)
        val rawBookmarkPaths = cursor.stringNullable(KEY_BOOKMARK_PATHS)
        val rawBookmarkTitles = cursor.stringNullable(KEY_BOOKMARK_TITLES)

        val bookmarkPositions = if (rawBookmarkPositions == null) IntArray(0) else convertToStringArray(rawBookmarkPositions.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        val bookmarkPaths = if (rawBookmarkPaths == null) arrayOf<String>() else rawBookmarkPaths.split(stringSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val bookmarkTitles = if (rawBookmarkTitles == null) arrayOf<String>() else rawBookmarkTitles.split(stringSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        val bookmarks = ImmutableList.copyOf(generateBookmarks(bookmarkPositions, bookmarkPaths, bookmarkTitles)
                .sorted())

        val bookId = cursor.long(BookTable.ID)
        val bookName = cursor.string(BookTable.NAME)
        val bookAuthor = cursor.stringNullable(BookTable.AUTHOR)
        val currentPath = File(cursor.string(BookTable.CURRENT_MEDIA_PATH))
        val bookSpeed = cursor.float(BookTable.PLAYBACK_SPEED)
        val bookRoot = cursor.string(BookTable.ROOT)
        val bookTime = cursor.int(BookTable.TIME)
        val bookType = Book.Type.valueOf(cursor.string(BookTable.TYPE))
        val bookUseCoverReplacement = cursor.int(BookTable.USE_COVER_REPLACEMENT) == BOOLEAN_TRUE

        return Book(bookId,
                bookmarks,
                bookType,
                bookUseCoverReplacement,
                bookAuthor,
                currentPath,
                bookTime,
                bookName,
                chapters,
                bookSpeed,
                bookRoot)
    }
}
