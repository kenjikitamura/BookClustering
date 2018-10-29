package jp.rainbowdevil.textdetection

class Dict {
    val words = mutableSetOf<Word>()
    var nextId = 0
    fun addWord(str: String) {
        var contains = false
        words.forEach{
            if (it.surface == str) {
                contains = true
            }
        }

        if (!contains) {
            val word = Word().apply {
                surface = str
                id = nextId
            }
            words.add(word)
            nextId++
        }
    }

    fun toVector(list: List<String>) : IntArray {
        val arr = IntArray(words.size)
        val inputWords = mutableListOf<Word>()
        list.forEach {
            val str = it
            words.forEach {
                if (str == it.surface) {
                    inputWords.add(it)
                }
            }
        }

        inputWords.forEach {
            arr[it.id] = 1
        }
        return arr
    }
}

class Document {
    var words = mutableListOf<Word>()
}

class Word {
    var surface: String = ""
    var id: Int = 0
}