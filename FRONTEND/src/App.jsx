import { useEffect, useState } from 'react';
import axios from 'axios';
import { TemplateProvider } from './contexts/TemplateContext';
import { TemplateSelector } from './components/TemplateSelector/TemplateSelector';
import PostInputForm from './components/PostForm/PostInputForm';

function App() {
  const [message, setMessage] = useState('연동 확인 중...');

  useEffect(() => {
    axios
      .get('/api/health')
      .then((res) => {
        setMessage(res.data.message);
      })
      .catch((err) => {
        console.error('CORS 또는 네트워크 에러 발생:', err);
        setMessage('통신 실패! F12 콘솔창을 확인하세요.');
      });
  }, []);

  return (
    <TemplateProvider>
      <div style={{ padding: '40px', fontFamily: 'sans-serif' }}>
        <h1>🤝 프론트-백엔드 연동 테스트</h1>
        <p>
          백엔드 응답 결과: <strong style={{ color: 'blue' }}>{message}</strong>
        </p>

        <hr style={{ margin: '30px 0' }} />

        {/* 템플릿 선택 컴포넌트 */}
        <TemplateSelector />

        {/* 게시글 입력 폼 컴포넌트 */}
        <PostInputForm />
      </div>
    </TemplateProvider>
  );
}

export default App;
