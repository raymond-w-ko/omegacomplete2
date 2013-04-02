" script load guard
if exists('g:omegacomplete2_loaded')
    finish
endif
let g:omegacomplete2_loaded = 1
if !has('java')
    finish
end

let s:omegacomplete2_path = ''

function s:Init()
    let path_list = split(&runtimepath, ',')
    for path in path_list
        let candidate_path = path . '/clojure/com/raymondwko/omegacomplete2'
        if isdirectory(candidate_path)
            let s:omegacomplete2_path = candidate_path
            break
        endif
    endfor
    if len(s:omegacomplete2_path) == 0
        echoerr 'could not find omegacomplete2 Clojure directory'
        return
    end

    call s:LoadMappings()

    " if we are running in Win32, we have to convert backslashes as the
    " Clojure reader doesn't like them
    let s:omegacomplete2_path = substitute(s:omegacomplete2_path, '\\', '/', 'g')

    call <SID>ReloadClojure()
    java (com.raymondwko.omegacomplete2/init)
endfunction

function <SID>ReloadClojure()
    javashell clojure
    exe 'java (load-string (slurp "' . s:omegacomplete2_path . '/core.clj"))'
endfunction

function s:LoadMappings()
    command Omegacomplete2Reload :call <SID>ReloadClojure()
endfunction

function omegacomplete2#OnBufferInsertModeChange()
    javashell clojure
    exe 'java (com.raymondwko.omegacomplete2/capture-buffer ' . bufnr('%') . ')'
endfunction

augroup omegacomplete2
    autocmd!
    autocmd CursorMovedI * call omegacomplete2#OnBufferInsertModeChange()
augroup END

call s:Init()
